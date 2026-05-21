package com.aidocker.agent.repository;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RepositoryCloneExceptionHandler {

    @ExceptionHandler(RepositoryCloneException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleRepositoryCloneException(RepositoryCloneException exception) {
        return Map.of("message", exception.getMessage());
    }
}
