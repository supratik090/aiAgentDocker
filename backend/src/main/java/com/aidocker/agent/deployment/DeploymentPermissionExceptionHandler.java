package com.aidocker.agent.deployment;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DeploymentPermissionExceptionHandler {

    @ExceptionHandler(DeploymentPermissionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleDeploymentPermissionException(DeploymentPermissionException exception) {
        return Map.of("message", exception.getMessage());
    }
}
