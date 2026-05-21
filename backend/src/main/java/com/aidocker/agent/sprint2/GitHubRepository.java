package com.aidocker.agent.sprint2;

import java.net.URI;

public record GitHubRepository(
        String owner,
        String name
) {

    public static GitHubRepository fromHttpsUrl(String gitUrl) {
        URI uri = URI.create(gitUrl.replace(".git", ""));
        String[] segments = uri.getPath().replaceFirst("^/", "").split("/");
        if (segments.length < 2) {
            throw new IllegalArgumentException("Git URL must include GitHub owner and repository name.");
        }

        return new GitHubRepository(segments[0], segments[1]);
    }
}
