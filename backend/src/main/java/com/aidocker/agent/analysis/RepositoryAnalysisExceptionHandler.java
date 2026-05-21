package com.aidocker.agent.analysis;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RepositoryAnalysisExceptionHandler {

    @ExceptionHandler(RepositoryAnalysisException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleRepositoryAnalysisException(RepositoryAnalysisException exception) {
        return Map.of("message", exception.getMessage());
    }
}
