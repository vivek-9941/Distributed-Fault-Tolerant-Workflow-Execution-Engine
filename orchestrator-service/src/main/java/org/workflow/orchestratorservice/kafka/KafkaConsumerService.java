package org.workflow.orchestratorservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.workflow.coremodels.event.TaskCompletedEvent;
import org.workflow.coremodels.event.TaskFailedEvent;
import org.workflow.coremodels.event.WorkflowStartedEvent;
import org.workflow.orchestratorservice.service.OrchestratorService;

@Component
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final OrchestratorService orchestratorService;

    public KafkaConsumerService(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @KafkaListener(topics = "workflow.started", groupId = "orchestrator-group",
            properties = {"spring.json.value.default.type=org.workflow.coremodels.event.WorkflowStartedEvent"})
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        log.info("Received workflow.started event: workflowRunId={}", event.workflowRunId());
        try {
            orchestratorService.handleWorkflowStarted(event);
        } catch (Exception e) {
            log.error("Error processing workflow.started event for {}: {}", event.workflowRunId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "task.completed", groupId = "orchestrator-group",
            properties = {"spring.json.value.default.type=org.workflow.coremodels.event.TaskCompletedEvent"})
    public void onTaskCompleted(TaskCompletedEvent event) {
        log.info("Received task.completed event: taskRunId={}", event.taskRunId());
        try {
            orchestratorService.handleTaskCompleted(event);
        } catch (Exception e) {
            log.error("Error processing task.completed event for {}: {}", event.taskRunId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "task.failed", groupId = "orchestrator-group",
            properties = {"spring.json.value.default.type=org.workflow.coremodels.event.TaskFailedEvent"})
    public void onTaskFailed(TaskFailedEvent event) {
        log.info("Received task.failed event: taskRunId={}", event.taskRunId());
        try {
            orchestratorService.handleTaskFailed(event);
        } catch (Exception e) {
            log.error("Error processing task.failed event for {}: {}", event.taskRunId(), e.getMessage(), e);
        }
    }
}
