package com.aidocker.agent.conversation;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final Clock clock;

    public ConversationService(ConversationRepository conversationRepository, Clock clock) {
        this.conversationRepository = conversationRepository;
        this.clock = clock;
    }

    public ConversationResponse create(CreateConversationRequest request) {
        Instant now = Instant.now(clock);
        Conversation conversation = new Conversation(
                request.gitUrl(),
                ConversationStatus.GIT_URL_RECEIVED,
                now,
                now
        );

        return ConversationResponse.from(conversationRepository.save(conversation));
    }
}
