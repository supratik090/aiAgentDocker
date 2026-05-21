package com.aidocker.agent.sprint2;

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
@RequestMapping("/api/sprint2")
public class Sprint2Controller {

    private final Sprint2PullRequestService sprint2PullRequestService;

    public Sprint2Controller(Sprint2PullRequestService sprint2PullRequestService) {
        this.sprint2PullRequestService = sprint2PullRequestService;
    }

    @PostMapping("/dummy-pull-request")
    @ResponseStatus(HttpStatus.CREATED)
    CreateDummyPullRequestResponse createDummyPullRequest(
            @AuthenticationPrincipal OAuth2User user,
            @Valid @RequestBody CreateDummyPullRequestRequest request
    ) {
        return sprint2PullRequestService.createDummyPullRequest(
                user.getName(),
                GitHubUser.from(user),
                request
        );
    }
}
