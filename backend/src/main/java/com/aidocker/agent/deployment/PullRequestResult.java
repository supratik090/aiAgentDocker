package com.aidocker.agent.deployment;

public record PullRequestResult(
        String url,
        Integer number
) {
}
