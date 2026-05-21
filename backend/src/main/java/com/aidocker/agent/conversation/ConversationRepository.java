package com.aidocker.agent.conversation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByGithubUserIdOrderByUpdatedAtDesc(String githubUserId);

    Optional<Conversation> findByIdAndGithubUserId(String id, String githubUserId);
}
