package com.aidocker.agent.deployment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("docker_ai_reviews")
public class DockerAiReview {

    @Id
    private String id;

    @Indexed
    private String repositoryWorkspaceId;

    @Indexed
    private String conversationId;

    @Indexed
    private String githubUserId;

    private String prompt;
    private String rawResponse;
    private String summary;
    private List<String> updatedFiles;
    private Instant createdAt;

    public DockerAiReview() {
    }

    public DockerAiReview(
            String repositoryWorkspaceId,
            String conversationId,
            String githubUserId,
            String prompt,
            String rawResponse,
            String summary,
            List<String> updatedFiles,
            Instant createdAt
    ) {
        this.repositoryWorkspaceId = repositoryWorkspaceId;
        this.conversationId = conversationId;
        this.githubUserId = githubUserId;
        this.prompt = prompt;
        this.rawResponse = rawResponse;
        this.summary = summary;
        this.updatedFiles = updatedFiles;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRepositoryWorkspaceId() {
        return repositoryWorkspaceId;
    }

    public void setRepositoryWorkspaceId(String repositoryWorkspaceId) {
        this.repositoryWorkspaceId = repositoryWorkspaceId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getGithubUserId() {
        return githubUserId;
    }

    public void setGithubUserId(String githubUserId) {
        this.githubUserId = githubUserId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getUpdatedFiles() {
        return updatedFiles;
    }

    public void setUpdatedFiles(List<String> updatedFiles) {
        this.updatedFiles = updatedFiles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
