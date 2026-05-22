package com.aidocker.agent.deployment;

import com.aidocker.agent.auth.GitHubUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployment-permissions")
public class DeploymentPermissionController {

    private final PullRequestPermissionService pullRequestPermissionService;
    private final DockerConfigGenerationService dockerConfigGenerationService;

    public DeploymentPermissionController(
            PullRequestPermissionService pullRequestPermissionService,
            DockerConfigGenerationService dockerConfigGenerationService
    ) {
        this.pullRequestPermissionService = pullRequestPermissionService;
        this.dockerConfigGenerationService = dockerConfigGenerationService;
    }

    @PostMapping("/pull-request-check")
    @ResponseStatus(HttpStatus.CREATED)
    CreatePermissionCheckPullRequestResponse createPermissionCheckPullRequest(
            @AuthenticationPrincipal OAuth2User user,
            @Valid @RequestBody CreatePermissionCheckPullRequestRequest request
    ) {
        return pullRequestPermissionService.createPermissionCheckPullRequest(
                user.getName(),
                GitHubUser.from(user),
                request
        );
    }

    @PostMapping("/docker-configs")
    @ResponseStatus(HttpStatus.CREATED)
    GenerateDockerConfigsResponse generateDockerConfigs(
            @AuthenticationPrincipal OAuth2User user,
            @Valid @RequestBody GenerateDockerConfigsRequest request
    ) {
        return dockerConfigGenerationService.generateDockerConfigs(
                user.getName(),
                GitHubUser.from(user),
                request
        );
    }
}
