package org.workflow.workflowapi.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workflow.coremodels.event.WorkflowStartedEvent;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishWorkflowStarted(WorkflowStartedEvent event) {
        kafkaTemplate.send("workflow.started", event.workflowRunId().toString(), event);
    }
}
