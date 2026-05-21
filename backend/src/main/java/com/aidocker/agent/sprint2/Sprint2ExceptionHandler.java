package com.aidocker.agent.sprint2;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class Sprint2ExceptionHandler {

    @ExceptionHandler(Sprint2Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleSprint2Exception(Sprint2Exception exception) {
        return Map.of("message", exception.getMessage());
    }
}
