package com.aidocker.agent.analysis;

import java.util.List;

public record NpmMetadata(
        String path,
        String name,
        String version,
        String mainScript,
        List<String> scripts,
        boolean executableCandidate
) {
}
