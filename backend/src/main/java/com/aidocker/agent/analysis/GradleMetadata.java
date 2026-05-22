package com.aidocker.agent.analysis;

public record GradleMetadata(
        String path,
        String group,
        String artifactId,
        String version,
        String javaVersion,
        boolean springBootProject,
        boolean executableCandidate
) {
}
