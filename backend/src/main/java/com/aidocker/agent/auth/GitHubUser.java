package com.aidocker.agent.auth;

import org.springframework.security.oauth2.core.user.OAuth2User;

public record GitHubUser(
        String githubUserId,
        String githubLogin
) {

    public static GitHubUser from(OAuth2User user) {
        Object id = user.getAttribute("id");
        Object login = user.getAttribute("login");

        return new GitHubUser(
                id == null ? user.getName() : id.toString(),
                login == null ? user.getName() : login.toString()
        );
    }
}
