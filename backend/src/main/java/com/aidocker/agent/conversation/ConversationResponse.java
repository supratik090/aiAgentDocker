package com.aidocker.agent.conversation;

import java.time.Instant;

public record ConversationResponse(
        String id,
        String gitUrl,
        ConversationStatus status,
        Instant createdAt,
        Instant updatedAt,
        String assistantMessage
) {

    static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getGitUrl(),
                conversation.getStatus(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                "Great, I have the repository URL. In the next sprint I will inspect it and ask the setup questions needed for Docker, CI/CD, and Kubernetes."
        );
    }
}
