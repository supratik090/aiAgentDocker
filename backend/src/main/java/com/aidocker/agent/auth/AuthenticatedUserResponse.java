package com.aidocker.agent.auth;

public record AuthenticatedUserResponse(
        boolean authenticated,
        String login,
        String name,
        String avatarUrl
) {

    static AuthenticatedUserResponse anonymous() {
        return new AuthenticatedUserResponse(false, null, null, null);
    }
}
