package org.workflow.orchestratorservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workflow.coremodels.event.TaskCompletedEvent;
import org.workflow.coremodels.event.TaskFailedEvent;
import org.workflow.coremodels.event.TaskReadyEvent;
import org.workflow.coremodels.event.WorkflowStartedEvent;
import org.workflow.coremodels.model.*;
import org.workflow.coremodels.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final TaskRunRepository taskRunRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final OrchestratorKafkaProducerService kafkaProducerService;

    public OrchestratorService(TaskRunRepository taskRunRepository,
                               TaskDefinitionRepository taskDefinitionRepository,
                               TaskDependencyRepository taskDependencyRepository,
                               WorkflowRunRepository workflowRunRepository,
                               OrchestratorKafkaProducerService kafkaProducerService) {
        this.taskRunRepository = taskRunRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Called when a workflow.started event is received.
     * Performs initial DAG analysis using in-degree reduction:
     * - Tasks with zero dependencies are immediately marked READY and published.
     */
    @Transactional
    public void handleWorkflowStarted(WorkflowStartedEvent event) {
        log.info("Processing workflow.started for workflowRunId={}", event.workflowRunId());

        // Update workflow run status to RUNNING
        WorkflowRun workflowRun = workflowRunRepository.findById(event.workflowRunId())
                .orElseThrow(() -> new RuntimeException("WorkflowRun not found: " + event.workflowRunId()));
        workflowRun.setStatus(WorkflowRun.Status.RUNNING);
        workflowRun.setStartedAt(LocalDateTime.now());
        workflowRunRepository.save(workflowRun);

        // Get all task runs for this workflow run
        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(event.workflowRunId());

        // Get all dependencies for this workflow
        UUID workflowId = event.workflowId();
        List<TaskDependency> allDeps = taskDependencyRepository.findByWorkflowId(workflowId);

        // Build a set of task definition IDs that have parents
        Set<String> tasksWithParents = allDeps.stream()
                .map(TaskDependency::getChildTaskId)
                .collect(Collectors.toSet());

        // Tasks with zero in-degree (no parents) are ready immediately
        for (TaskRun taskRun : taskRuns) {
            if (!tasksWithParents.contains(taskRun.getTaskDefinitionId())) {
                markTaskReady(taskRun, event.workflowRunId());
            }
        }

        log.info("Workflow {} initialized. {} tasks total, {} ready immediately.",
                event.workflowRunId(), taskRuns.size(),
                taskRuns.stream().filter(t -> !tasksWithParents.contains(t.getTaskDefinitionId())).count());
    }

    /**
     * Called when a task.completed event is received.
     * Decrements the in-degree of child tasks and activates any that become unblocked.
     * Checks if the entire workflow is complete.
     */
    @Transactional
    public void handleTaskCompleted(TaskCompletedEvent event) {
        log.info("Processing task.completed for taskRunId={}, taskDefId={}",
                event.taskRunId(), event.taskDefinitionId());

        // Verify the task run is in RUNNING state
        TaskRun completedTask = taskRunRepository.findById(event.taskRunId()).orElse(null);
        if (completedTask == null || completedTask.getStatus() != TaskRun.Status.RUNNING) {
            log.warn("Task {} is not in RUNNING state, ignoring completion event", event.taskRunId());
            return;
        }

        // Mark the task as SUCCESS
        completedTask.setStatus(TaskRun.Status.SUCCESS);
        completedTask.setCompletedAt(LocalDateTime.now());
        taskRunRepository.save(completedTask);

        // Find all children of this completed task
        List<TaskDependency> childDeps = taskDependencyRepository.findByParentTaskId(event.taskDefinitionId());

        for (TaskDependency dep : childDeps) {
            String childTaskDefId = dep.getChildTaskId();

            // Check if ALL parents of this child are now SUCCESS
            List<TaskDependency> parentDeps = taskDependencyRepository.findByChildTaskId(childTaskDefId);
            boolean allParentsDone = true;

            for (TaskDependency parentDep : parentDeps) {
                TaskRun parentTaskRun = taskRunRepository
                        .findByWorkflowRunIdAndTaskDefinitionId(event.workflowRunId(), parentDep.getParentTaskId())
                        .orElse(null);
                if (parentTaskRun == null || parentTaskRun.getStatus() != TaskRun.Status.SUCCESS) {
                    allParentsDone = false;
                    break;
                }
            }

            if (allParentsDone) {
                // Find the child's task run and mark it READY
                TaskRun childTaskRun = taskRunRepository
                        .findByWorkflowRunIdAndTaskDefinitionId(event.workflowRunId(), childTaskDefId)
                        .orElse(null);
                if (childTaskRun != null && childTaskRun.getStatus() == TaskRun.Status.PENDING) {
                    markTaskReady(childTaskRun, event.workflowRunId());
                    log.info("Task {} is now unblocked and READY", childTaskDefId);
                }
            }
        }

        // Check if the entire workflow is complete
        checkWorkflowCompletion(event.workflowRunId());
    }

    /**
     * Called when a task.failed event is received.
     * Implements retry logic: re-queues the task if under max_retries, otherwise fails the workflow.
     */
    @Transactional
    public void handleTaskFailed(TaskFailedEvent event) {
        log.info("Processing task.failed for taskRunId={}, retry={}, error={}",
                event.taskRunId(), event.retryCount(), event.errorMessage());

        TaskRun failedTask = taskRunRepository.findById(event.taskRunId()).orElse(null);
        if (failedTask == null) {
            log.warn("Task run {} not found, ignoring failure event", event.taskRunId());
            return;
        }

        // Get the task definition for max_retries
        TaskDefinition taskDef = taskDefinitionRepository.findById(event.taskDefinitionId()).orElse(null);
        int maxRetries = (taskDef != null && taskDef.getMaxRetries() != null) ? taskDef.getMaxRetries() : 3;

        if (event.retryCount() < maxRetries) {
            // Retry: reset to READY and increment retry count
            failedTask.setStatus(TaskRun.Status.READY);
            failedTask.setRetryCount(event.retryCount() + 1);
            failedTask.setWorkerId(null);
            failedTask.setStartedAt(null);
            failedTask.setErrorMessage(event.errorMessage());
            taskRunRepository.save(failedTask);

            // Republish to task.ready
            String command = (taskDef != null) ? taskDef.getCommand() : "echo No command";
            kafkaProducerService.publishTaskReady(new TaskReadyEvent(
                    failedTask.getId(),
                    failedTask.getTaskDefinitionId(),
                    event.workflowRunId(),
                    command
            ));

            log.info("Task {} retrying (attempt {}/{})", event.taskRunId(), event.retryCount() + 1, maxRetries);
        } else {
            // Max retries exhausted — move to DLQ and fail the workflow
            failedTask.setStatus(TaskRun.Status.DLQ);
            failedTask.setErrorMessage(event.errorMessage());
            failedTask.setCompletedAt(LocalDateTime.now());
            taskRunRepository.save(failedTask);

            // Fail the entire workflow run
            WorkflowRun workflowRun = workflowRunRepository.findById(event.workflowRunId()).orElse(null);
            if (workflowRun != null) {
                workflowRun.setStatus(WorkflowRun.Status.FAILED);
                workflowRunRepository.save(workflowRun);
            }

            log.error("Task {} exhausted retries ({}/{}). Moved to DLQ. Workflow {} FAILED.",
                    event.taskRunId(), event.retryCount(), maxRetries, event.workflowRunId());
        }
    }

    /**
     * Marks a task as READY and publishes a TaskReadyEvent to Kafka
     */
    private void markTaskReady(TaskRun taskRun, UUID workflowRunId) {
        taskRun.setStatus(TaskRun.Status.READY);
        taskRunRepository.save(taskRun);

        // Get the command from task definition
        TaskDefinition taskDef = taskDefinitionRepository.findById(taskRun.getTaskDefinitionId()).orElse(null);
        String command = (taskDef != null && taskDef.getCommand() != null)
                ? taskDef.getCommand() : "echo No command specified";

        kafkaProducerService.publishTaskReady(new TaskReadyEvent(
                taskRun.getId(),
                taskRun.getTaskDefinitionId(),
                workflowRunId,
                command
        ));
    }

    /**
     * Checks if all tasks in a workflow run are complete (SUCCESS or FAILED).
     * If all are SUCCESS → mark workflow as COMPLETED.
     */
    private void checkWorkflowCompletion(UUID workflowRunId) {
        List<TaskRun> allTasks = taskRunRepository.findByWorkflowRunId(workflowRunId);
        boolean allDone = allTasks.stream().allMatch(t ->
                t.getStatus() == TaskRun.Status.SUCCESS || t.getStatus() == TaskRun.Status.FAILED || t.getStatus() == TaskRun.Status.DLQ);
        boolean allSuccess = allTasks.stream().allMatch(t -> t.getStatus() == TaskRun.Status.SUCCESS);

        if (allDone) {
            WorkflowRun workflowRun = workflowRunRepository.findById(workflowRunId).orElse(null);
            if (workflowRun != null && workflowRun.getStatus() == WorkflowRun.Status.RUNNING) {
                workflowRun.setStatus(allSuccess ? WorkflowRun.Status.COMPLETED : WorkflowRun.Status.FAILED);
                workflowRunRepository.save(workflowRun);
                log.info("Workflow {} is now {}", workflowRunId,
                        allSuccess ? "COMPLETED" : "FAILED");
            }
        }
    }
}
