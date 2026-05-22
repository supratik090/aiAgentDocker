package com.aidocker.agent.deployment;

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
public class PullRequestPermissionService {

    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final GitHubAccessTokenService gitHubAccessTokenService;
    private final DeploymentBranchService deploymentBranchService;
    private final PermissionCheckCommitService permissionCheckCommitService;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final ConversationService conversationService;
    private final Clock clock;

    public PullRequestPermissionService(
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            GitHubAccessTokenService gitHubAccessTokenService,
            DeploymentBranchService deploymentBranchService,
            PermissionCheckCommitService permissionCheckCommitService,
            GitHubPullRequestService gitHubPullRequestService,
            ConversationService conversationService,
            Clock clock
    ) {
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.gitHubAccessTokenService = gitHubAccessTokenService;
        this.deploymentBranchService = deploymentBranchService;
        this.permissionCheckCommitService = permissionCheckCommitService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.conversationService = conversationService;
        this.clock = clock;
    }

    public CreatePermissionCheckPullRequestResponse createPermissionCheckPullRequest(
            String principalName,
            GitHubUser user,
            CreatePermissionCheckPullRequestRequest request
    ) {
        RepositoryWorkspace workspace = repositoryWorkspaceRepository
                .findByIdAndGithubUserId(request.repositoryWorkspaceId(), user.githubUserId())
                .orElseThrow(() -> new DeploymentPermissionException("Repository workspace was not found for the logged-in user."));

        String accessToken = gitHubAccessTokenService.tokenFor(principalName);
        Path repositoryPath = Path.of(workspace.getLocalPath());

        try {
            String deploymentBranch = deploymentBranchService.createDeployReadyBranch(repositoryPath);
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

            String commitId = permissionCheckCommitService.createPermissionCheckCommitAndPush(repositoryPath, deploymentBranch, accessToken);
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
            PullRequestResult pullRequest = gitHubPullRequestService.createPullRequest(
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

            return new CreatePermissionCheckPullRequestResponse(
                    workspace.getId(),
                    deploymentBranch,
                    commitId,
                    pullRequest.url(),
                    pullRequest.number(),
                    "PR_CREATED",
                    "Permission check pull request completed.\nBranch: " + deploymentBranch + "\nCommit: " + commitId + "\nPull request:\n" + pullRequest.url() + "\n\nNext: I will ask for Docker, CI/CD, and Kubernetes details."
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

    private String baseBranch(CreatePermissionCheckPullRequestRequest request, RepositoryWorkspace workspace) {
        if (StringUtils.hasText(request.baseBranch())) {
            return request.baseBranch().trim();
        }
        if (StringUtils.hasText(workspace.getBranch())) {
            return workspace.getBranch();
        }
        return "main";
    }
}
