package org.workflow.coremodels.event;

import java.util.UUID;

public record WorkflowStartedEvent(
        UUID workflowRunId,
        UUID workflowId,
        String triggeredBy
) {}
