package com.aidocker.agent.repository;

public record CloneRepositoryResponse(
        String repositoryWorkspaceId,
        String gitUrl,
        String branch,
        String localPath,
        String status
) {
}
