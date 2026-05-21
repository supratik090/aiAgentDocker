package com.aidocker.agent.sprint2;

import jakarta.validation.constraints.NotBlank;

public record CreateDummyPullRequestRequest(
        @NotBlank
        String repositoryWorkspaceId,
        String baseBranch
) {
}
