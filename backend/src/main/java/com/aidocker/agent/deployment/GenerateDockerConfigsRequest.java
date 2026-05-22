package com.aidocker.agent.deployment;

import jakarta.validation.constraints.NotBlank;

public record GenerateDockerConfigsRequest(
        @NotBlank
        String repositoryWorkspaceId
) {
}
