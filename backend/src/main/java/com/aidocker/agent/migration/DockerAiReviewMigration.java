package com.aidocker.agent.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "docker-ai-review-indexes", order = "003", author = "ai-docker-agent")
public class DockerAiReviewMigration {

    private final MongoTemplate mongoTemplate;

    public DockerAiReviewMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void createIndexes() {
        mongoTemplate.indexOps("docker_ai_reviews")
                .ensureIndex(new Index().on("conversationId", Sort.Direction.ASC));
        mongoTemplate.indexOps("docker_ai_reviews")
                .ensureIndex(new Index().on("repositoryWorkspaceId", Sort.Direction.ASC));
        mongoTemplate.indexOps("docker_ai_reviews")
                .ensureIndex(new Index().on("githubUserId", Sort.Direction.ASC));
    }

    @RollbackExecution
    public void rollback() {
        // Rollback is intentionally empty for the schema migration.
    }
}
