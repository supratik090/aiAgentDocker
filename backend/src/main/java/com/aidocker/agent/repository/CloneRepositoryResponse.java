package com.aidocker.agent.repository;

import java.util.List;

public record CloneRepositoryResponse(
        String repositoryWorkspaceId,
        String gitUrl,
        String branch,
        String localPath,
        String repositoryAnalysisId,
        String repositoryAnalysisPath,
        List<String> executableModuleCandidates,
        String status,
        String nextAction,
        String assistantMessage
) {
}
