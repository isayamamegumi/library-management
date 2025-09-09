package com.library.management.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.library.management.service.SecurityLogService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class SecurityExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityExceptionHandler.class);
    
    @Autowired
    private SecurityLogService securityLogService;
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException e, WebRequest request) {
        
        String ipAddress = securityLogService.getClientIpAddress();
        securityLogService.logFailedLogin("Unknown", ipAddress, "Bad credentials");
        
        logger.warn("Authentication failed - Bad credentials from IP: {}", ipAddress);
        
        Map<String, Object> errorResponse = createErrorResponse(
            "INVALID_CREDENTIALS", 
            "ユーザー名またはパスワードが間違っています",
            HttpStatus.UNAUTHORIZED
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException e, WebRequest request) {
        
        String ipAddress = securityLogService.getClientIpAddress();
        String username = request.getRemoteUser() != null ? request.getRemoteUser() : "Anonymous";
        String resource = request.getDescription(false);
        
        securityLogService.logAccessDenied(username, resource, ipAddress);
        
        logger.warn("Access denied for user: {} to resource: {}", username, resource);
        
        Map<String, Object> errorResponse = createErrorResponse(
            "ACCESS_DENIED", 
            "この操作を実行する権限がありません",
            HttpStatus.FORBIDDEN
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException e, WebRequest request) {
        
        Map<String, Object> errorResponse = createErrorResponse(
            "AUTHENTICATION_FAILED", 
            "認証に失敗しました。ログインしてください",
            HttpStatus.UNAUTHORIZED
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException e, WebRequest request) {
        
        if (e.getMessage().contains("User not found") || e.getMessage().contains("Book not found")) {
            Map<String, Object> errorResponse = createErrorResponse(
                "RESOURCE_NOT_FOUND", 
                e.getMessage(),
                HttpStatus.NOT_FOUND
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        
        Map<String, Object> errorResponse = createErrorResponse(
            "INTERNAL_SERVER_ERROR", 
            "サーバー内部エラーが発生しました",
            HttpStatus.INTERNAL_SERVER_ERROR
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientAuthentication(
            InsufficientAuthenticationException e, WebRequest request) {
        
        logger.warn("Insufficient authentication for resource: {}", 
            request.getDescription(false));
        
        Map<String, Object> errorResponse = createErrorResponse(
            "INSUFFICIENT_AUTHENTICATION", 
            "認証が必要です。ログインしてください",
            HttpStatus.UNAUTHORIZED
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException e, WebRequest request) {
        
        logger.warn("Validation error in request: {}", request.getDescription(false));
        
        Map<String, String> validationErrors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "VALIDATION_FAILED");
        errorResponse.put("message", "入力値に不正があります");
        errorResponse.put("validationErrors", validationErrors);
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBookNotFound(
            BookNotFoundException e, WebRequest request) {
        
        logger.warn("Book not found: {}", e.getMessage());
        
        Map<String, Object> errorResponse = createErrorResponse(
            "BOOK_NOT_FOUND", 
            e.getMessage(),
            HttpStatus.NOT_FOUND
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UserNotFoundException e, WebRequest request) {
        
        logger.warn("User not found: {}", e.getMessage());
        
        Map<String, Object> errorResponse = createErrorResponse(
            "USER_NOT_FOUND", 
            e.getMessage(),
            HttpStatus.NOT_FOUND
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(
            Exception e, WebRequest request) {
        
        logger.error("Unexpected error occurred: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = createErrorResponse(
            "INTERNAL_SERVER_ERROR", 
            "サーバー内部エラーが発生しました",
            HttpStatus.INTERNAL_SERVER_ERROR
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    private Map<String, Object> createErrorResponse(String errorCode, String message, HttpStatus status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", errorCode);
        errorResponse.put("message", message);
        errorResponse.put("status", status.value());
        errorResponse.put("timestamp", LocalDateTime.now());
        return errorResponse;
    }
}