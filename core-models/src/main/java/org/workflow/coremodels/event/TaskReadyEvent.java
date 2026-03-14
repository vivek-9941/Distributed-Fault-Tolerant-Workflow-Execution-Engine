package org.workflow.coremodels.event;

import java.util.UUID;

public record TaskReadyEvent(
        UUID taskRunId,
        String taskDefinitionId,
        UUID workflowRunId,
        String command
) {}
