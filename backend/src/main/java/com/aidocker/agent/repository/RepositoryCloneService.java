package com.aidocker.agent.repository;

import com.aidocker.agent.auth.GitHubUser;
import com.aidocker.agent.conversation.ConversationService;
import com.aidocker.agent.conversation.ConversationStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RepositoryCloneService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final ConversationService conversationService;
    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final Path cloneRoot;
    private final Clock clock;

    public RepositoryCloneService(
            OAuth2AuthorizedClientService authorizedClientService,
            ConversationService conversationService,
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            @Value("${app.repository.clone-root}") String cloneRoot,
            Clock clock
    ) {
        this.authorizedClientService = authorizedClientService;
        this.conversationService = conversationService;
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.cloneRoot = Path.of(cloneRoot).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public CloneRepositoryResponse cloneRepository(String principalName, GitHubUser user, CloneRepositoryRequest request) {
        OAuth2AuthorizedClient githubClient = authorizedClientService.loadAuthorizedClient("github", principalName);
        String accessToken = Optional.ofNullable(githubClient)
                .map(OAuth2AuthorizedClient::getAccessToken)
                .map(token -> token.getTokenValue())
                .orElseThrow(() -> new IllegalStateException("GitHub OAuth token is not available. Please login again."));

        Path destination = destinationFor(request.gitUrl(), user.githubUserId());
        String requestedBranch = StringUtils.hasText(request.branch()) ? request.branch().trim() : null;
        Instant now = Instant.now(clock);
        RepositoryWorkspace workspace = repositoryWorkspaceRepository.save(new RepositoryWorkspace(
                request.conversationId(),
                request.gitUrl(),
                user.githubUserId(),
                user.githubLogin(),
                requestedBranch,
                destination.toString(),
                RepositoryWorkspaceStatus.CLONING,
                now,
                now
        ));

        conversationService.updateRepositoryState(
                user.githubUserId(),
                request.conversationId(),
                ConversationStatus.CLONING,
                workspace.getId(),
                requestedBranch,
                destination.toString()
        );

        try {
            Files.createDirectories(destination.getParent());
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(request.gitUrl())
                    .setDirectory(destination.toFile())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", accessToken));

            if (requestedBranch != null) {
                cloneCommand.setBranch(requestedBranch);
            }

            try (Git git = cloneCommand.call()) {
                String actualBranch = git.getRepository().getBranch();
                workspace.setBranch(actualBranch);
                workspace.markCloned(Instant.now(clock));
                repositoryWorkspaceRepository.save(workspace);
                conversationService.updateRepositoryState(
                        user.githubUserId(),
                        request.conversationId(),
                        ConversationStatus.CLONED,
                        workspace.getId(),
                        actualBranch,
                        destination.toString()
                );
                return new CloneRepositoryResponse(
                        workspace.getId(),
                        request.gitUrl(),
                        actualBranch,
                        destination.toString(),
                        "CLONED"
                );
            }
        } catch (Exception exception) {
            workspace.markFailed(exception.getMessage(), Instant.now(clock));
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    user.githubUserId(),
                    request.conversationId(),
                    ConversationStatus.CLONE_FAILED,
                    workspace.getId(),
                    requestedBranch,
                    destination.toString()
            );
            throw new RepositoryCloneException("Unable to clone repository. Confirm the Git URL and GitHub access permissions.", exception);
        }
    }

    private Path destinationFor(String gitUrl, String githubUserId) {
        String repoName = gitUrl.substring(gitUrl.lastIndexOf('/') + 1)
                .replace(".git", "")
                .replaceAll("[^A-Za-z0-9._-]", "-");
        String ownerSegment = githubUserId.replaceAll("[^A-Za-z0-9._-]", "-");
        String timestamp = String.valueOf(clock.instant().toEpochMilli());

        try {
            Files.createDirectories(cloneRoot);
        } catch (IOException exception) {
            throw new RepositoryCloneException("Unable to create repository clone workspace.", exception);
        }

        return cloneRoot.resolve(ownerSegment).resolve(repoName + "-" + timestamp).normalize();
    }
}
