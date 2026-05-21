package com.aidocker.agent.repository;

public enum RepositoryWorkspaceStatus {
    CLONING,
    CLONED,
    CLONE_FAILED,
    BRANCH_READY,
    PUSHED,
    PR_CREATED,
    PR_FAILED
}
