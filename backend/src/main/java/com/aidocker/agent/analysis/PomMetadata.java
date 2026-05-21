package com.aidocker.agent.analysis;

public record PomMetadata(
        String path,
        String groupId,
        String artifactId,
        String version,
        String packaging,
        String javaVersion,
        boolean springBootProject,
        boolean executableCandidate
) {
}
