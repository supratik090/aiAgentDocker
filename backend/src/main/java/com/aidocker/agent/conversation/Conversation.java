package com.aidocker.agent.conversation;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("conversations")
public class Conversation {

    @Id
    private String id;

    @Indexed
    private String gitUrl;

    @Indexed
    private String githubUserId;
    private String githubLogin;
    private ConversationStatus status;
    private String repositoryWorkspaceId;
    private String repositoryBranch;
    private String repositoryLocalPath;
    private Instant createdAt;
    private Instant updatedAt;

    public Conversation() {
    }

    public Conversation(
            String gitUrl,
            String githubUserId,
            String githubLogin,
            ConversationStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.gitUrl = gitUrl;
        this.githubUserId = githubUserId;
        this.githubLogin = githubLogin;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
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

    public ConversationStatus getStatus() {
        return status;
    }

    public void setStatus(ConversationStatus status) {
        this.status = status;
    }

    public String getRepositoryWorkspaceId() {
        return repositoryWorkspaceId;
    }

    public void setRepositoryWorkspaceId(String repositoryWorkspaceId) {
        this.repositoryWorkspaceId = repositoryWorkspaceId;
    }

    public String getRepositoryBranch() {
        return repositoryBranch;
    }

    public void setRepositoryBranch(String repositoryBranch) {
        this.repositoryBranch = repositoryBranch;
    }

    public String getRepositoryLocalPath() {
        return repositoryLocalPath;
    }

    public void setRepositoryLocalPath(String repositoryLocalPath) {
        this.repositoryLocalPath = repositoryLocalPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
