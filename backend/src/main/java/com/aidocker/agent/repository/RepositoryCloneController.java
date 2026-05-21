package com.aidocker.agent.repository;

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
@RequestMapping("/api/repositories")
public class RepositoryCloneController {

    private final RepositoryCloneService repositoryCloneService;

    public RepositoryCloneController(RepositoryCloneService repositoryCloneService) {
        this.repositoryCloneService = repositoryCloneService;
    }

    @PostMapping("/clone")
    @ResponseStatus(HttpStatus.CREATED)
    CloneRepositoryResponse cloneRepository(
            @AuthenticationPrincipal OAuth2User user,
            @Valid @RequestBody CloneRepositoryRequest request
    ) {
        return repositoryCloneService.cloneRepository(user.getName(), GitHubUser.from(user), request);
    }
}
