package org.workflow.coremodels.event;

import java.util.UUID;

public record TaskCompletedEvent(
        UUID taskRunId,
        UUID workflowRunId,
        String taskDefinitionId
) {}
