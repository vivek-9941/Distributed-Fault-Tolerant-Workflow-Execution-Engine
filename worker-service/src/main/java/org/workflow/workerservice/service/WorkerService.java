package org.workflow.workerservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workflow.coremodels.event.TaskCompletedEvent;
import org.workflow.coremodels.event.TaskFailedEvent;
import org.workflow.coremodels.event.TaskReadyEvent;
import org.workflow.coremodels.model.TaskDefinition;
import org.workflow.coremodels.model.TaskLog;
import org.workflow.coremodels.model.TaskRun;
import org.workflow.coremodels.repository.TaskDefinitionRepository;
import org.workflow.coremodels.repository.TaskLogRepository;
import org.workflow.coremodels.repository.TaskRunRepository;
import org.workflow.workerservice.kafka.WorkerKafkaProducerService;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final TaskRunRepository taskRunRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskLogRepository taskLogRepository;
    private final TaskExecutorService taskExecutorService;
    private final WorkerKafkaProducerService kafkaProducerService;
    private final Executor taskExecutor;

    @Value("${worker.id:worker-1}")
    private String workerId;

    public WorkerService(TaskRunRepository taskRunRepository,
                         TaskDefinitionRepository taskDefinitionRepository,
                         TaskLogRepository taskLogRepository,
                         TaskExecutorService taskExecutorService,
                         WorkerKafkaProducerService kafkaProducerService,
                         @Qualifier("taskExecutor") Executor taskExecutor) {
        this.taskRunRepository = taskRunRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskLogRepository = taskLogRepository;
        this.taskExecutorService = taskExecutorService;
        this.kafkaProducerService = kafkaProducerService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Called when a task.ready event is consumed from Kafka.
     * Claims the task atomically and submits it to the thread pool for execution.
     */
    public void handleTaskReady(TaskReadyEvent event) {
        log.info("Worker {} received task.ready: taskRunId={}, command={}",
                workerId, event.taskRunId(), event.command());

        // Attempt to claim the task atomically
        // This prevents duplicate processing across multiple workers
        int updated = taskRunRepository.claimTask(
                event.taskRunId(),
                TaskRun.Status.RUNNING,
                workerId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                TaskRun.Status.READY
        );

        if (updated == 0) {
            log.info("Task {} already claimed by another worker, skipping", event.taskRunId());
            return;
        }

        log.info("Worker {} claimed task {}", workerId, event.taskRunId());

        // Submit task execution to thread pool (non-blocking)
        taskExecutor.execute(() -> executeTask(event));
    }

    /**
     * Executes the task command and reports the result.
     */
    private void executeTask(TaskReadyEvent event) {
        try {
            // Get timeout from task definition
            TaskDefinition taskDef = taskDefinitionRepository.findById(event.taskDefinitionId()).orElse(null);
            int timeoutSeconds = (taskDef != null && taskDef.getTimeoutSeconds() != null)
                    ? taskDef.getTimeoutSeconds() : 300;

            // Execute the command
            TaskExecutorService.TaskExecutionResult result =
                    taskExecutorService.executeCommand(event.command(), timeoutSeconds);

            // Save logs
            saveTaskLogs(event.taskRunId(), result.stdout(), result.stderr());

            if (result.success()) {
                handleSuccess(event);
            } else {
                handleFailure(event, result.errorMessage());
            }

        } catch (Exception e) {
            log.error("Unexpected error executing task {}: {}", event.taskRunId(), e.getMessage(), e);
            handleFailure(event, "Unexpected error: " + e.getMessage());
        }
    }

    private void handleSuccess(TaskReadyEvent event) {
        // Update task status in DB
        TaskRun taskRun = taskRunRepository.findById(event.taskRunId()).orElse(null);
        if (taskRun != null) {
            taskRun.setStatus(TaskRun.Status.SUCCESS);
            taskRun.setCompletedAt(LocalDateTime.now());
            taskRunRepository.save(taskRun);
        }

        // Publish completion event
        kafkaProducerService.publishTaskCompleted(new TaskCompletedEvent(
                event.taskRunId(),
                event.workflowRunId(),
                event.taskDefinitionId()
        ));

        log.info("Task {} completed successfully", event.taskRunId());
    }

    private void handleFailure(TaskReadyEvent event, String errorMessage) {
        TaskRun taskRun = taskRunRepository.findById(event.taskRunId()).orElse(null);
        int retryCount = 0;
        if (taskRun != null) {
            retryCount = taskRun.getRetryCount();
            taskRun.setStatus(TaskRun.Status.FAILED);
            taskRun.setErrorMessage(errorMessage);
            taskRun.setCompletedAt(LocalDateTime.now());
            taskRunRepository.save(taskRun);
        }

        // Publish failure event (orchestrator handles retry logic)
        kafkaProducerService.publishTaskFailed(new TaskFailedEvent(
                event.taskRunId(),
                event.workflowRunId(),
                event.taskDefinitionId(),
                errorMessage,
                retryCount
        ));

        log.error("Task {} failed: {}", event.taskRunId(), errorMessage);
    }

    private void saveTaskLogs(java.util.UUID taskRunId, String stdout, String stderr) {
        try {
            StringBuilder logContent = new StringBuilder();
            if (stdout != null && !stdout.isBlank()) {
                logContent.append("=== STDOUT ===\n").append(stdout).append("\n");
            }
            if (stderr != null && !stderr.isBlank()) {
                logContent.append("=== STDERR ===\n").append(stderr).append("\n");
            }

            if (logContent.length() > 0) {
                TaskLog taskLog = new TaskLog();
                taskLog.setTaskRunId(taskRunId);
                taskLog.setLogContent(logContent.toString());
                taskLogRepository.save(taskLog);
            }
        } catch (Exception e) {
            log.warn("Failed to save task logs for {}: {}", taskRunId, e.getMessage());
        }
    }
}
