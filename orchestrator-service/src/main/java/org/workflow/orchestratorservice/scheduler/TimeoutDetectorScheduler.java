package org.workflow.orchestratorservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.workflow.coremodels.event.TaskReadyEvent;
import org.workflow.coremodels.model.TaskDefinition;
import org.workflow.coremodels.model.TaskRun;
import org.workflow.coremodels.repository.TaskDefinitionRepository;
import org.workflow.coremodels.repository.TaskRunRepository;
import org.workflow.orchestratorservice.service.OrchestratorKafkaProducerService;
import java.time.LocalDateTime;
import java.util.List;
/**
 * Periodically checks for tasks stuck in RUNNING state with stale heartbeats.
 * Resets them to READY for re-execution (application-level self-healing).
 */
@Component
public class TimeoutDetectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutDetectorScheduler.class);

    private final TaskRunRepository taskRunRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final OrchestratorKafkaProducerService kafkaProducerService;

    @Value("${orchestrator.timeout.heartbeat-threshold-seconds:120}")
    private int heartbeatThresholdSeconds;

    public TimeoutDetectorScheduler(TaskRunRepository taskRunRepository,
                                     TaskDefinitionRepository taskDefinitionRepository,
                                     OrchestratorKafkaProducerService kafkaProducerService) {
        this.taskRunRepository = taskRunRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Scheduled(fixedDelayString = "${orchestrator.timeout.check-interval-ms:30000}")
    @Transactional
    public void detectStalledTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(heartbeatThresholdSeconds);
        List<TaskRun> stalledTasks = taskRunRepository.findByStatusAndLastHeartbeatBefore(
                TaskRun.Status.RUNNING, threshold);

        if (!stalledTasks.isEmpty()) {
            log.warn("Detected {} stalled tasks with heartbeat older than {}s",
                    stalledTasks.size(), heartbeatThresholdSeconds);
        }

        for (TaskRun task : stalledTasks) {
            log.warn("Resetting stalled task: id={}, taskDefId={}, lastHeartbeat={}",
                    task.getId(), task.getTaskDefinitionId(), task.getLastHeartbeat());

            task.setStatus(TaskRun.Status.READY);
            task.setRetryCount(task.getRetryCount() + 1);
            task.setWorkerId(null);
            task.setStartedAt(null);
            task.setErrorMessage("Task timed out — no heartbeat received");
            taskRunRepository.save(task);

            // Get command from task definition
            TaskDefinition taskDef = taskDefinitionRepository.findById(task.getTaskDefinitionId()).orElse(null);
            String command = (taskDef != null && taskDef.getCommand() != null)
                    ? taskDef.getCommand() : "echo No command";

            kafkaProducerService.publishTaskReady(new TaskReadyEvent(
                    task.getId(),
                    task.getTaskDefinitionId(),
                    task.getWorkflowRunId(),
                    command
            ));
        }
    }
}
