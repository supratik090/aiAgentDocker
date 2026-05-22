package com.aidocker.agent.deployment;

public record CreatePermissionCheckPullRequestResponse(
        String repositoryWorkspaceId,
        String deploymentBranch,
        String commitId,
        String pullRequestUrl,
        Integer pullRequestNumber,
        String status,
        String assistantMessage
) {
}
