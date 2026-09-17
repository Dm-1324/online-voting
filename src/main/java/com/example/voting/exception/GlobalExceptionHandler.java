package com.example.voting.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String ERROR_KEY = "error";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
    }

    @ExceptionHandler(AlreadyVotedException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyVoted(AlreadyVotedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(ERROR_KEY, e.getMessage()));
    }

    @ExceptionHandler(PollClosedException.class)
    public ResponseEntity<Map<String, String>> handlePollClosed(PollClosedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(ERROR_KEY, e.getMessage()));
    }
}
