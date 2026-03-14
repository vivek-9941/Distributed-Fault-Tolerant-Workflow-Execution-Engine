package org.workflow.orchestratorservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workflow.coremodels.event.TaskReadyEvent;

@Service
public class OrchestratorKafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorKafkaProducerService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrchestratorKafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTaskReady(TaskReadyEvent event) {
        log.info("Publishing task.ready for taskRunId={}, command={}", event.taskRunId(), event.command());
        kafkaTemplate.send("task.ready", event.workflowRunId().toString(), event);
    }
}
