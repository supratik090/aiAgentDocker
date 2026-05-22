package com.aidocker.agent.repository;

import com.aidocker.agent.analysis.RepositoryAnalysisResponse;
import com.aidocker.agent.analysis.RepositoryAnalysisException;
import com.aidocker.agent.analysis.RepositoryAnalysisService;
import com.aidocker.agent.auth.GitHubUser;
import com.aidocker.agent.conversation.ConversationService;
import com.aidocker.agent.conversation.ConversationStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RepositoryCloneService {

    private final GitHubAccessTokenService gitHubAccessTokenService;
    private final RepositoryAnalysisService repositoryAnalysisService;
    private final ConversationService conversationService;
    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final Path cloneRoot;
    private final Clock clock;

    public RepositoryCloneService(
            GitHubAccessTokenService gitHubAccessTokenService,
            RepositoryAnalysisService repositoryAnalysisService,
            ConversationService conversationService,
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            @Value("${app.repository.clone-root}") String cloneRoot,
            Clock clock
    ) {
        this.gitHubAccessTokenService = gitHubAccessTokenService;
        this.repositoryAnalysisService = repositoryAnalysisService;
        this.conversationService = conversationService;
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.cloneRoot = Path.of(cloneRoot).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public CloneRepositoryResponse cloneRepository(String principalName, GitHubUser user, CloneRepositoryRequest request) {
        String accessToken = gitHubAccessTokenService.tokenFor(principalName);

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
                RepositoryAnalysisResponse analysis;
                try {
                    analysis = repositoryAnalysisService.analyze(workspace);
                } catch (RepositoryAnalysisException exception) {
                    return new CloneRepositoryResponse(
                            workspace.getId(),
                            request.gitUrl(),
                            actualBranch,
                            destination.toString(),
                            null,
                            null,
                            List.of(),
                            "ANALYSIS_FAILED",
                            null,
                            "Repository cloned, but analysis failed.\nWorkspace: " + destination + "\nBranch: " + actualBranch + "\nError: " + exception.getMessage()
                    );
                }
                return new CloneRepositoryResponse(
                        workspace.getId(),
                        request.gitUrl(),
                        actualBranch,
                        destination.toString(),
                        analysis.repositoryAnalysisId(),
                        analysis.analysisArtifactPath(),
                        analysis.executableModuleCandidates(),
                        "ANALYZED",
                        analysis.executableModuleCandidates().isEmpty() ? "ASK_PULL_REQUEST_PERMISSION_CHECK" : "ASK_EXECUTABLE_MODULES",
                        cloneAnalysisMessage(destination, actualBranch, analysis)
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

    private String cloneAnalysisMessage(Path destination, String actualBranch, RepositoryAnalysisResponse analysis) {
        String baseMessage = "Repository cloned and analyzed.\nWorkspace: " + destination
                + "\nBranch: " + actualBranch
                + "\nAnalysis: " + analysis.analysisArtifactPath();
        if (!analysis.executableModuleCandidates().isEmpty()) {
            return baseMessage + "\n\nI found multiple executable module candidates. Please confirm which modules should be packaged as deployable services.";
        }
        return baseMessage + "\n\nCreate a permission check pull request to confirm branch, push, and PR permissions?";
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
