package com.aidocker.agent.analysis;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RepositoryAnalysisRepository extends MongoRepository<RepositoryAnalysis, String> {

    List<RepositoryAnalysis> findByGithubUserIdOrderByUpdatedAtDesc(String githubUserId);

    List<RepositoryAnalysis> findByConversationIdAndGithubUserIdOrderByUpdatedAtDesc(String conversationId, String githubUserId);

    Optional<RepositoryAnalysis> findByIdAndGithubUserId(String id, String githubUserId);
}
