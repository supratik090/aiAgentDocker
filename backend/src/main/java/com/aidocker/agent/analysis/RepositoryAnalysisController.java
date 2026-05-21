package com.aidocker.agent.analysis;

import com.aidocker.agent.auth.GitHubUser;
import com.aidocker.agent.repository.RepositoryWorkspace;
import com.aidocker.agent.repository.RepositoryWorkspaceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class RepositoryAnalysisController {

    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final RepositoryAnalysisService repositoryAnalysisService;

    public RepositoryAnalysisController(
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            RepositoryAnalysisService repositoryAnalysisService
    ) {
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.repositoryAnalysisService = repositoryAnalysisService;
    }

    @PostMapping("/{repositoryWorkspaceId}")
    @ResponseStatus(HttpStatus.CREATED)
    RepositoryAnalysisResponse analyze(
            @AuthenticationPrincipal OAuth2User user,
            @PathVariable String repositoryWorkspaceId
    ) {
        GitHubUser gitHubUser = GitHubUser.from(user);
        RepositoryWorkspace workspace = repositoryWorkspaceRepository
                .findByIdAndGithubUserId(repositoryWorkspaceId, gitHubUser.githubUserId())
                .orElseThrow(() -> new RepositoryAnalysisException("Repository workspace was not found for the logged-in user.", null));
        return repositoryAnalysisService.analyze(workspace);
    }

    @PostMapping("/{repositoryAnalysisId}/executable-modules")
    RepositoryAnalysisResponse selectExecutableModules(
            @AuthenticationPrincipal OAuth2User user,
            @PathVariable String repositoryAnalysisId,
            @Valid @RequestBody SelectExecutableModulesRequest request
    ) {
        GitHubUser gitHubUser = GitHubUser.from(user);
        return repositoryAnalysisService.selectExecutableModules(
                gitHubUser.githubUserId(),
                repositoryAnalysisId,
                request.executableModules()
        );
    }
}
