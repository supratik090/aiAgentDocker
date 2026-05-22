package com.aidocker.agent.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "initial-conversation-indexes", order = "001", author = "ai-docker-agent")
public class InitialMongoMigration {

    private final MongoTemplate mongoTemplate;

    public InitialMongoMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void createIndexes() {
        mongoTemplate.indexOps("conversations")
                .ensureIndex(new Index().on("gitUrl", Sort.Direction.ASC));
        mongoTemplate.indexOps("conversations")
                .ensureIndex(new Index().on("githubUserId", Sort.Direction.ASC));
        mongoTemplate.indexOps("repository_workspaces")
                .ensureIndex(new Index().on("conversationId", Sort.Direction.ASC));
        mongoTemplate.indexOps("repository_workspaces")
                .ensureIndex(new Index().on("gitUrl", Sort.Direction.ASC));
        mongoTemplate.indexOps("repository_workspaces")
                .ensureIndex(new Index().on("githubUserId", Sort.Direction.ASC));
    }

    @RollbackExecution
    public void rollback() {
        // Index rollback is intentionally empty for the initial conversation schema.
    }
}
