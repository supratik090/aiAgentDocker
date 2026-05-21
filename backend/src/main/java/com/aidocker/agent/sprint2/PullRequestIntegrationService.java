package com.aidocker.agent.sprint2;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PullRequestIntegrationService {

    private final RestClient restClient;

    public PullRequestIntegrationService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public PullRequestResult createPullRequest(
            String accessToken,
            GitHubRepository repository,
            String branchName,
            String baseBranch
    ) {
        Map<?, ?> response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls", repository.owner(), repository.name())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title", "AI Docker Agent deployment setup",
                        "head", branchName,
                        "base", baseBranch,
                        "body", "Permission check PR from AI Docker Agent. This dummy file verifies branch creation, commit, push, and pull request access before Docker, CI/CD, and Kubernetes files are generated."
                ))
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new Sprint2Exception("GitHub did not return a pull request response.");
        }

        Object htmlUrl = response.get("html_url");
        Object number = response.get("number");
        return new PullRequestResult(
                htmlUrl == null ? null : htmlUrl.toString(),
                number instanceof Number pullRequestNumber ? pullRequestNumber.intValue() : null
        );
    }
}
