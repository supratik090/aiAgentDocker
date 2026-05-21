package com.aidocker.agent.conversation;

public enum ConversationStatus {
    STARTED,
    GIT_URL_RECEIVED,
    CLONING,
    CLONED,
    CLONE_FAILED,
    BRANCH_READY,
    PUSHED,
    PR_CREATED,
    PR_FAILED
}
