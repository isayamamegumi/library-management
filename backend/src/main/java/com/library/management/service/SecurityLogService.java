package com.library.management.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SecurityLogService {
    
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    public void logSuccessfulLogin(String username, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "LOGIN_SUCCESS");
        logData.put("username", username);
        logData.put("ipAddress", ipAddress);
        
        securityLogger.info("Successful login - User: {}, IP: {}, Details: {}", 
            username, ipAddress, logData);
    }
    
    public void logFailedLogin(String username, String ipAddress, String reason) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "LOGIN_FAILED");
        logData.put("username", username);
        logData.put("ipAddress", ipAddress);
        logData.put("reason", reason);
        
        securityLogger.warn("Failed login attempt - User: {}, IP: {}, Reason: {}, Details: {}", 
            username, ipAddress, reason, logData);
    }
    
    public void logUserRegistration(String username, String email, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "USER_REGISTRATION");
        logData.put("username", username);
        logData.put("email", email);
        logData.put("ipAddress", ipAddress);
        
        auditLogger.info("New user registration - User: {}, Email: {}, IP: {}, Details: {}", 
            username, email, ipAddress, logData);
    }
    
    public void logAccessDenied(String username, String resource, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "ACCESS_DENIED");
        logData.put("username", username);
        logData.put("resource", resource);
        logData.put("ipAddress", ipAddress);
        
        securityLogger.warn("Access denied - User: {}, Resource: {}, IP: {}, Details: {}", 
            username, resource, ipAddress, logData);
    }
    
    public void logUnauthorizedAccess(String resource, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "UNAUTHORIZED_ACCESS");
        logData.put("resource", resource);
        logData.put("ipAddress", ipAddress);
        
        securityLogger.warn("Unauthorized access attempt - Resource: {}, IP: {}, Details: {}", 
            resource, ipAddress, logData);
    }
    
    public void logBookOperation(String operation, String username, Long bookId, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "BOOK_OPERATION");
        logData.put("operation", operation);
        logData.put("username", username);
        logData.put("bookId", bookId);
        logData.put("ipAddress", ipAddress);
        
        auditLogger.info("Book operation - Operation: {}, User: {}, BookId: {}, IP: {}, Details: {}", 
            operation, username, bookId, ipAddress, logData);
    }
    
    public void logUserOperation(String operation, String adminUsername, String targetUsername, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "USER_OPERATION");
        logData.put("operation", operation);
        logData.put("adminUsername", adminUsername);
        logData.put("targetUsername", targetUsername);
        logData.put("ipAddress", ipAddress);
        
        auditLogger.info("User operation - Operation: {}, Admin: {}, Target: {}, IP: {}, Details: {}", 
            operation, adminUsername, targetUsername, ipAddress, logData);
    }
    
    public void logSuspiciousActivity(String activity, String username, String ipAddress, String details) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "SUSPICIOUS_ACTIVITY");
        logData.put("activity", activity);
        logData.put("username", username);
        logData.put("ipAddress", ipAddress);
        logData.put("details", details);
        
        securityLogger.error("Suspicious activity detected - Activity: {}, User: {}, IP: {}, Details: {}", 
            activity, username, ipAddress, logData);
    }
    
    public void logPasswordChange(String username, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "PASSWORD_CHANGE");
        logData.put("username", username);
        logData.put("ipAddress", ipAddress);
        
        auditLogger.info("Password changed - User: {}, IP: {}, Details: {}", 
            username, ipAddress, logData);
    }
    
    public void logLogout(String username, String ipAddress) {
        Map<String, Object> logData = createLogData();
        logData.put("event", "LOGOUT");
        logData.put("username", username);
        logData.put("ipAddress", ipAddress);
        
        securityLogger.info("User logout - User: {}, IP: {}, Details: {}", 
            username, ipAddress, logData);
    }
    
    private Map<String, Object> createLogData() {
        Map<String, Object> logData = new HashMap<>();
        logData.put("timestamp", LocalDateTime.now());
        logData.put("userAgent", getUserAgent());
        logData.put("sessionId", getSessionId());
        return logData;
    }
    
    private String getUserAgent() {
        try {
            HttpServletRequest request = getCurrentRequest();
            return request != null ? request.getHeader("User-Agent") : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private String getSessionId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            return request != null && request.getSession(false) != null 
                ? request.getSession(false).getId() : "No Session";
        } catch (Exception e) {
            return "No Session";
        }
    }
    
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    public String getClientIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) return "Unknown";
            
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}