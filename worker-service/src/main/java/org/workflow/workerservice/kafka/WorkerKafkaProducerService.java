package org.workflow.workerservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workflow.coremodels.event.TaskCompletedEvent;
import org.workflow.coremodels.event.TaskFailedEvent;

@Service
public class WorkerKafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerKafkaProducerService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WorkerKafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTaskCompleted(TaskCompletedEvent event) {
        log.info("Publishing task.completed for taskRunId={}", event.taskRunId());
        kafkaTemplate.send("task.completed", event.workflowRunId().toString(), event);
    }

    public void publishTaskFailed(TaskFailedEvent event) {
        log.info("Publishing task.failed for taskRunId={}", event.taskRunId());
        kafkaTemplate.send("task.failed", event.workflowRunId().toString(), event);
    }
}
