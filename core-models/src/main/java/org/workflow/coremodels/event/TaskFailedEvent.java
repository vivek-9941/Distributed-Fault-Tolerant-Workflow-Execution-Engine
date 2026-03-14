package org.workflow.coremodels.event;

import java.util.UUID;

public record TaskFailedEvent(
        UUID taskRunId,
        UUID workflowRunId,
        String taskDefinitionId,
        String errorMessage,
        int retryCount
) {}
