package com.aidocker.agent.sprint2;

public record CreateDummyPullRequestResponse(
        String repositoryWorkspaceId,
        String deploymentBranch,
        String commitId,
        String pullRequestUrl,
        Integer pullRequestNumber,
        String status,
        String assistantMessage
) {
}
