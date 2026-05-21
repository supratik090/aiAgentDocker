package com.aidocker.agent.sprint2;

import com.aidocker.agent.auth.GitHubUser;
import com.aidocker.agent.conversation.ConversationService;
import com.aidocker.agent.conversation.ConversationStatus;
import com.aidocker.agent.repository.GitHubAccessTokenService;
import com.aidocker.agent.repository.RepositoryWorkspace;
import com.aidocker.agent.repository.RepositoryWorkspaceRepository;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class Sprint2PullRequestService {

    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final GitHubAccessTokenService gitHubAccessTokenService;
    private final BranchManagementService branchManagementService;
    private final GitCommitPushService gitCommitPushService;
    private final PullRequestIntegrationService pullRequestIntegrationService;
    private final ConversationService conversationService;
    private final Clock clock;

    public Sprint2PullRequestService(
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            GitHubAccessTokenService gitHubAccessTokenService,
            BranchManagementService branchManagementService,
            GitCommitPushService gitCommitPushService,
            PullRequestIntegrationService pullRequestIntegrationService,
            ConversationService conversationService,
            Clock clock
    ) {
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.gitHubAccessTokenService = gitHubAccessTokenService;
        this.branchManagementService = branchManagementService;
        this.gitCommitPushService = gitCommitPushService;
        this.pullRequestIntegrationService = pullRequestIntegrationService;
        this.conversationService = conversationService;
        this.clock = clock;
    }

    public CreateDummyPullRequestResponse createDummyPullRequest(
            String principalName,
            GitHubUser user,
            CreateDummyPullRequestRequest request
    ) {
        RepositoryWorkspace workspace = repositoryWorkspaceRepository
                .findByIdAndGithubUserId(request.repositoryWorkspaceId(), user.githubUserId())
                .orElseThrow(() -> new Sprint2Exception("Repository workspace was not found for the logged-in user."));

        String accessToken = gitHubAccessTokenService.tokenFor(principalName);
        Path repositoryPath = Path.of(workspace.getLocalPath());

        try {
            String deploymentBranch = branchManagementService.createDeployReadyBranch(repositoryPath);
            workspace.markBranchReady(deploymentBranch, clock.instant());
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    user.githubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.BRANCH_READY,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );

            String commitId = gitCommitPushService.createDummyFileCommitAndPush(repositoryPath, deploymentBranch, accessToken);
            workspace.markPushed(commitId, clock.instant());
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    user.githubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.PUSHED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );

            String baseBranch = baseBranch(request, workspace);
            PullRequestResult pullRequest = pullRequestIntegrationService.createPullRequest(
                    accessToken,
                    GitHubRepository.fromHttpsUrl(workspace.getGitUrl()),
                    deploymentBranch,
                    baseBranch
            );
            workspace.markPullRequestCreated(pullRequest.url(), pullRequest.number(), clock.instant());
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    user.githubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.PR_CREATED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );

            return new CreateDummyPullRequestResponse(
                    workspace.getId(),
                    deploymentBranch,
                    commitId,
                    pullRequest.url(),
                    pullRequest.number(),
                    "PR_CREATED",
                    "Dummy PR permission check completed.\nBranch: " + deploymentBranch + "\nCommit: " + commitId + "\nPull request:\n" + pullRequest.url() + "\n\nNext: I will ask for Docker, CI/CD, and Kubernetes details."
            );
        } catch (RuntimeException exception) {
            workspace.markPullRequestFailed(exception.getMessage(), clock.instant());
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    user.githubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.PR_FAILED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );
            throw exception;
        }
    }

    private String baseBranch(CreateDummyPullRequestRequest request, RepositoryWorkspace workspace) {
        if (StringUtils.hasText(request.baseBranch())) {
            return request.baseBranch().trim();
        }
        if (StringUtils.hasText(workspace.getBranch())) {
            return workspace.getBranch();
        }
        return "main";
    }
}
