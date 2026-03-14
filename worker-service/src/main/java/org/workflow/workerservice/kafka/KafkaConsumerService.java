package org.workflow.workerservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.workflow.coremodels.event.TaskReadyEvent;
import org.workflow.workerservice.service.WorkerService;

@Component
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final WorkerService workerService;

    public KafkaConsumerService(WorkerService workerService) {
        this.workerService = workerService;
    }

    @KafkaListener(topics = "task.ready", groupId = "worker-group",
            properties = {"spring.json.value.default.type=org.workflow.coremodels.event.TaskReadyEvent"})
    public void onTaskReady(TaskReadyEvent event) {
        log.info("Received task.ready event: taskRunId={}", event.taskRunId());
        try {
            workerService.handleTaskReady(event);
        } catch (Exception e) {
            log.error("Error processing task.ready event for {}: {}", event.taskRunId(), e.getMessage(), e);
        }
    }
}
