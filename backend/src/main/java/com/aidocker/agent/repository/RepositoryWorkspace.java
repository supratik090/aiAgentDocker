package com.aidocker.agent.repository;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("repository_workspaces")
public class RepositoryWorkspace {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    @Indexed
    private String gitUrl;

    @Indexed
    private String githubUserId;
    private String githubLogin;
    private String branch;
    private String localPath;
    private String deploymentBranch;
    private String repositoryAnalysisId;
    private String repositoryAnalysisPath;
    private String lastCommitId;
    private String pullRequestUrl;
    private Integer pullRequestNumber;
    private RepositoryWorkspaceStatus status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public RepositoryWorkspace() {
    }

    public RepositoryWorkspace(
            String conversationId,
            String gitUrl,
            String githubUserId,
            String githubLogin,
            String branch,
            String localPath,
            RepositoryWorkspaceStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.conversationId = conversationId;
        this.gitUrl = gitUrl;
        this.githubUserId = githubUserId;
        this.githubLogin = githubLogin;
        this.branch = branch;
        this.localPath = localPath;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public String getGithubUserId() {
        return githubUserId;
    }

    public String getGithubLogin() {
        return githubLogin;
    }

    public String getBranch() {
        return branch;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getDeploymentBranch() {
        return deploymentBranch;
    }

    public String getRepositoryAnalysisId() {
        return repositoryAnalysisId;
    }

    public String getRepositoryAnalysisPath() {
        return repositoryAnalysisPath;
    }

    public String getLastCommitId() {
        return lastCommitId;
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    public Integer getPullRequestNumber() {
        return pullRequestNumber;
    }

    public RepositoryWorkspaceStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markCloned(Instant updatedAt) {
        this.status = RepositoryWorkspaceStatus.CLONED;
        this.errorMessage = null;
        this.updatedAt = updatedAt;
    }

    public void markAnalyzing(Instant updatedAt) {
        this.status = RepositoryWorkspaceStatus.ANALYZING;
        this.errorMessage = null;
        this.updatedAt = updatedAt;
    }

    public void markAnalyzed(String repositoryAnalysisId, String repositoryAnalysisPath, Instant updatedAt) {
        this.repositoryAnalysisId = repositoryAnalysisId;
        this.repositoryAnalysisPath = repositoryAnalysisPath;
        this.status = RepositoryWorkspaceStatus.ANALYZED;
        this.errorMessage = null;
        this.updatedAt = updatedAt;
    }

    public void markAnalysisFailed(String errorMessage, Instant updatedAt) {
        this.status = RepositoryWorkspaceStatus.ANALYSIS_FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = updatedAt;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public void markBranchReady(String deploymentBranch, Instant updatedAt) {
        this.deploymentBranch = deploymentBranch;
        this.status = RepositoryWorkspaceStatus.BRANCH_READY;
        this.errorMessage = null;
        this.updatedAt = updatedAt;
    }

    public void markPushed(String lastCommitId, Instant updatedAt) {
        this.lastCommitId = lastCommitId;
        this.status = RepositoryWorkspaceStatus.PUSHED;
        this.errorMessage = null;
        this.updatedAt = updatedAt;
    }

    public void markPullRequestCreated(String pullRequestUrl, Integer pullRequestNumber, Instant updatedAt) {
        this.pullRequestUrl = pullRequestUrl;
        this.pullRequestNumber = pullRequestNumber;
        this.status = RepositoryWorkspaceStatus.PR_CREATED;
        this.errorMessage = null;
        this.updatedAt = updatedAt;
    }

    public void markPullRequestFailed(String errorMessage, Instant updatedAt) {
        this.status = RepositoryWorkspaceStatus.PR_FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = updatedAt;
    }

    public void markDockerConfigsGenerated(Instant updatedAt) {
        this.status = RepositoryWorkspaceStatus.DOCKER_CONFIGS_GENERATED;
        this.errorMessage = null;
        this.updatedAt = updatedAt;
    }

    public void markDockerConfigsFailed(String errorMessage, Instant updatedAt) {
        this.status = RepositoryWorkspaceStatus.DOCKER_CONFIGS_FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = updatedAt;
    }

    public void markFailed(String errorMessage, Instant updatedAt) {
        this.status = RepositoryWorkspaceStatus.CLONE_FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = updatedAt;
    }
}
