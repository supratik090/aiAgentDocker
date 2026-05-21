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

    private ConversationStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public Conversation() {
    }

    public Conversation(String gitUrl, ConversationStatus status, Instant createdAt, Instant updatedAt) {
        this.gitUrl = gitUrl;
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

    public ConversationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
