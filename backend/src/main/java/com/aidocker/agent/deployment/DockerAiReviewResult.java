package com.aidocker.agent.deployment;

import java.util.List;

public record DockerAiReviewResult(
        List<String> updatedFiles,
        String summary
) {
}
