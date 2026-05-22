package com.aidocker.agent.deployment;

public class DeploymentPermissionException extends RuntimeException {

    public DeploymentPermissionException(String message) {
        super(message);
    }

    public DeploymentPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
