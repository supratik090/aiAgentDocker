package com.aidocker.agent.analysis;

import java.util.List;

public record RepositoryAnalysisResponse(
        String repositoryAnalysisId,
        String analysisArtifactPath,
        List<String> executableModuleCandidates,
        List<String> selectedExecutableModules,
        String status,
        String assistantMessage
) {
}
