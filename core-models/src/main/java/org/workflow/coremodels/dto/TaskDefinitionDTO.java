package org.workflow.coremodels.dto;

import java.util.List;

public record TaskDefinitionDTO(
        String id,
        String type,
        List<String> dependsOn,
        Integer timeoutSeconds,
        Integer maxRetries
) {}
