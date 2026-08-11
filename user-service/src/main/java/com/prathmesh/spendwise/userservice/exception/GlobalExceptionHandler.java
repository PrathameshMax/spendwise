package com.prathmesh.spendwise.userservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(ResourceNotFoundException.class)
    public ErrorResponse handleResourceNotFound(ResourceNotFoundException ex,
                                                HttpServletRequest request){
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                400,
               "Validation failed",
               "/api/v1/users",
                errors
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ErrorResponse handleGenericException(Exception ex, HttpServletRequest request){

        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred",
                request.getRequestURI(),
                null
        );

    }
}
