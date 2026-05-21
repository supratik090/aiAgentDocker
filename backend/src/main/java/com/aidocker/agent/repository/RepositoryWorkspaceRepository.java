package com.aidocker.agent.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RepositoryWorkspaceRepository extends MongoRepository<RepositoryWorkspace, String> {

    List<RepositoryWorkspace> findByGithubUserIdOrderByUpdatedAtDesc(String githubUserId);

    List<RepositoryWorkspace> findByConversationIdAndGithubUserIdOrderByUpdatedAtDesc(String conversationId, String githubUserId);

    Optional<RepositoryWorkspace> findByIdAndGithubUserId(String id, String githubUserId);
}
