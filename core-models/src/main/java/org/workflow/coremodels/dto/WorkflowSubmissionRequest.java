package org.workflow.coremodels.dto;

import java.util.List;

public record WorkflowSubmissionRequest(
        String workflowName,
        String repoUrl,
        String triggeredBy,
        List<TaskDefinitionDTO> tasks
) {}
