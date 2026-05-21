package com.aidocker.agent.repository;

import java.util.Optional;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

@Service
public class GitHubAccessTokenService {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public GitHubAccessTokenService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    public String tokenFor(String principalName) {
        OAuth2AuthorizedClient githubClient = authorizedClientService.loadAuthorizedClient("github", principalName);
        return Optional.ofNullable(githubClient)
                .map(OAuth2AuthorizedClient::getAccessToken)
                .map(token -> token.getTokenValue())
                .orElseThrow(() -> new IllegalStateException("GitHub OAuth token is not available. Please login again."));
    }
}
