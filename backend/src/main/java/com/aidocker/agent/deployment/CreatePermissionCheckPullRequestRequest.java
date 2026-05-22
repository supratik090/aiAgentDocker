package com.aidocker.agent.deployment;

import jakarta.validation.constraints.NotBlank;

public record CreatePermissionCheckPullRequestRequest(
        @NotBlank
        String repositoryWorkspaceId,
        String baseBranch
) {
}
