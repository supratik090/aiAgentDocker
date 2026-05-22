package com.aidocker.agent.deployment;

import java.util.List;

public record GenerateDockerConfigsResponse(
        String repositoryWorkspaceId,
        List<String> generatedFiles,
        String deploymentBranch,
        String commitId,
        String pullRequestUrl,
        Integer pullRequestNumber,
        String status,
        String assistantMessage
) {
}
