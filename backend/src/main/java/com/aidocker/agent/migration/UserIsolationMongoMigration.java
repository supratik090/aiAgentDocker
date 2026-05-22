package com.aidocker.agent.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "user-isolation-indexes", order = "002", author = "ai-docker-agent")
public class UserIsolationMongoMigration {

    private final MongoTemplate mongoTemplate;

    public UserIsolationMongoMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void createIndexes() {
        mongoTemplate.indexOps("conversations")
                .ensureIndex(new Index().on("githubUserId", Sort.Direction.ASC));
        mongoTemplate.indexOps("repository_workspaces")
                .ensureIndex(new Index().on("githubUserId", Sort.Direction.ASC));
        mongoTemplate.indexOps("repository_analysis")
                .ensureIndex(new Index().on("repositoryWorkspaceId", Sort.Direction.ASC));
        mongoTemplate.indexOps("repository_analysis")
                .ensureIndex(new Index().on("conversationId", Sort.Direction.ASC));
        mongoTemplate.indexOps("repository_analysis")
                .ensureIndex(new Index().on("githubUserId", Sort.Direction.ASC));
    }

    @RollbackExecution
    public void rollback() {
        // Index rollback is intentionally empty for the user isolation schema.
    }
}
