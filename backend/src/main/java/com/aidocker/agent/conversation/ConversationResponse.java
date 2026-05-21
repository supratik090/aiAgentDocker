package com.aidocker.agent.conversation;

import java.time.Instant;

public record ConversationResponse(
        String id,
        String gitUrl,
        String githubLogin,
        ConversationStatus status,
        String repositoryWorkspaceId,
        String repositoryBranch,
        String repositoryLocalPath,
        Instant createdAt,
        Instant updatedAt,
        String assistantMessage
) {

    static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getGitUrl(),
                conversation.getGithubLogin(),
                conversation.getStatus(),
                conversation.getRepositoryWorkspaceId(),
                conversation.getRepositoryBranch(),
                conversation.getRepositoryLocalPath(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                "Repository URL received.\nNext: I will clone it and then ask whether to run a dummy PR permission check."
        );
    }
}
