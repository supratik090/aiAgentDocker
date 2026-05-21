package com.aidocker.agent.conversation;

import com.aidocker.agent.auth.GitHubUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final Clock clock;

    public ConversationService(ConversationRepository conversationRepository, Clock clock) {
        this.conversationRepository = conversationRepository;
        this.clock = clock;
    }

    public ConversationResponse create(GitHubUser user, CreateConversationRequest request) {
        Instant now = Instant.now(clock);
        Conversation conversation = new Conversation(
                request.gitUrl(),
                user.githubUserId(),
                user.githubLogin(),
                ConversationStatus.GIT_URL_RECEIVED,
                now,
                now
        );

        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    public List<ConversationResponse> findForUser(GitHubUser user) {
        return conversationRepository.findByGithubUserIdOrderByUpdatedAtDesc(user.githubUserId())
                .stream()
                .map(ConversationResponse::from)
                .toList();
    }

    public void updateRepositoryState(
            String githubUserId,
            String conversationId,
            ConversationStatus status,
            String repositoryWorkspaceId,
            String repositoryBranch,
            String repositoryLocalPath
    ) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        conversationRepository.findByIdAndGithubUserId(conversationId, githubUserId)
                .ifPresent(conversation -> {
                    conversation.setStatus(status);
                    conversation.setRepositoryWorkspaceId(repositoryWorkspaceId);
                    conversation.setRepositoryBranch(repositoryBranch);
                    conversation.setRepositoryLocalPath(repositoryLocalPath);
                    conversation.setUpdatedAt(Instant.now(clock));
                    conversationRepository.save(conversation);
                });
    }
}
