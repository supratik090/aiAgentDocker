package com.aidocker.agent.deployment;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DockerAiReviewRepository extends MongoRepository<DockerAiReview, String> {

    List<DockerAiReview> findByConversationIdAndGithubUserIdOrderByCreatedAtDesc(String conversationId, String githubUserId);
}
