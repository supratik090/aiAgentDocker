package com.aidocker.agent.deployment;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubPullRequestService {

    private final RestClient restClient;

    public GitHubPullRequestService(RestClient.Builder restClientBuilder) {
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
        return createPullRequest(
                accessToken,
                repository,
                branchName,
                baseBranch,
                "AI Docker Agent deployment setup",
                "Permission check pull request from AI Docker Agent. This verification file confirms branch creation, commit, push, and pull request access before Docker, CI/CD, and Kubernetes files are generated."
        );
    }

    public PullRequestResult createPullRequest(
            String accessToken,
            GitHubRepository repository,
            String branchName,
            String baseBranch,
            String title,
            String body
    ) {
        Map<?, ?> response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls", repository.owner(), repository.name())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title", title,
                        "head", branchName,
                        "base", baseBranch,
                        "body", body
                ))
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new DeploymentPermissionException("GitHub did not return a pull request response.");
        }

        Object htmlUrl = response.get("html_url");
        Object number = response.get("number");
        return new PullRequestResult(
                htmlUrl == null ? null : htmlUrl.toString(),
                number instanceof Number pullRequestNumber ? pullRequestNumber.intValue() : null
        );
    }
}
