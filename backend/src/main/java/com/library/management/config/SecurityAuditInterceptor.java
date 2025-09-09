package com.library.management.config;

import com.library.management.service.SecurityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityAuditInterceptor implements HandlerInterceptor {
    
    @Autowired
    private SecurityLogService securityLogService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String ipAddress = securityLogService.getClientIpAddress();
        
        // セキュリティ関連のエンドポイントをログ記録
        if (isSecurityEndpoint(requestUri)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser") 
                ? auth.getName() : "Anonymous";
                
            securityLogService.logBookOperation(method, username, extractBookId(requestUri), ipAddress);
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) throws Exception {
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String ipAddress = securityLogService.getClientIpAddress();
        int statusCode = response.getStatus();
        
        // エラーレスポンスのログ記録
        if (statusCode == 401) {
            securityLogService.logUnauthorizedAccess(requestUri, ipAddress);
        } else if (statusCode == 403) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null && auth.isAuthenticated() 
                ? auth.getName() : "Anonymous";
            securityLogService.logAccessDenied(username, requestUri, ipAddress);
        }
        
        // 異常なアクセスパターンの検出
        if (isSuspiciousRequest(request, statusCode)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "Anonymous";
            securityLogService.logSuspiciousActivity(
                "Unusual access pattern", 
                username, 
                ipAddress, 
                String.format("URI: %s, Method: %s, Status: %d", requestUri, method, statusCode)
            );
        }
    }
    
    private boolean isSecurityEndpoint(String uri) {
        return uri.startsWith("/api/books") || 
               uri.startsWith("/api/users") || 
               uri.startsWith("/api/admin");
    }
    
    private Long extractBookId(String uri) {
        try {
            if (uri.matches(".*/books/\\d+.*")) {
                String[] parts = uri.split("/");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("books".equals(parts[i]) && i + 1 < parts.length) {
                        return Long.parseLong(parts[i + 1]);
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
        return null;
    }
    
    private boolean isSuspiciousRequest(HttpServletRequest request, int statusCode) {
        String userAgent = request.getHeader("User-Agent");
        String requestUri = request.getRequestURI();
        
        // 疑わしいパターンの検出
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return true;
        }
        
        // SQLインジェクション試行の検出
        if (requestUri.toLowerCase().contains("union") || 
            requestUri.toLowerCase().contains("select") ||
            requestUri.toLowerCase().contains("drop") ||
            requestUri.toLowerCase().contains("insert")) {
            return true;
        }
        
        // 異常な数の401/403エラー
        if (statusCode == 401 || statusCode == 403) {
            // 実際の実装では、IPアドレス単位でのエラー回数をカウントする
            return false;
        }
        
        return false;
    }
}