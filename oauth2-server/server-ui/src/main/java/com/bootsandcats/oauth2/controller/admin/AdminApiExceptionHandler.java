package com.bootsandcats.oauth2.controller.admin;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bootsandcats.oauth2.service.admin.AdminOperationNotAllowedException;
import com.bootsandcats.oauth2.service.admin.AdminResourceNotFoundException;

@RestControllerAdvice
public class AdminApiExceptionHandler {

    @ExceptionHandler(AdminResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(AdminResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(AdminOperationNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleNotAllowed(AdminOperationNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "NOT_ALLOWED", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "VALIDATION", "message", "Validation failed"));
    }
}
