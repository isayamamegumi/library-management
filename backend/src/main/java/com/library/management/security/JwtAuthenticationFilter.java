package com.library.management.security;

import com.library.management.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * JWT認証フィルター
 * HTTPリクエストからJWTトークンを抽出・検証し、認証情報をSpring Securityコンテキストに設定
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    /**
     * JWTトークンによる認証処理を実行
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // HTTPリクエストからJWTトークンを抽出
            String jwt = parseJwt(request);
            
            if (jwt != null && jwtUtil.validateJwtToken(jwt)) {
                // トークンからユーザー名を抽出
                String username = jwtUtil.getUsernameFromJwtToken(jwt);
                
                // ユーザー詳細情報を取得
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                // 認証オブジェクトを作成
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails, 
                        null, 
                        userDetails.getAuthorities()
                    );
                
                // リクエストの詳細情報を設定
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Spring Securityコンテキストに認証情報を設定
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                System.out.println("=== JWT Authentication Success ===");
                System.out.println("User: " + username);
                System.out.println("Authorities: " + userDetails.getAuthorities());
                logger.debug("Set Authentication in security context for user: " + username);
            }
        } catch (Exception e) {
            System.out.println("=== JWT Authentication Failed ===");
            System.out.println("Error: " + e.getMessage());
            logger.error("Cannot set user authentication: " + e.getMessage(), e);
            
            // エラー時はコンテキストをクリア
            SecurityContextHolder.clearContext();
        }
        
        // 次のフィルターに処理を委譲
        filterChain.doFilter(request, response);
    }
    
    /**
     * HTTPリクエストのHeaderからJWTトークンを抽出
     * @param request HTTPリクエスト
     * @return JWTトークン（存在しない場合はnull）
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // "Bearer "を除去
        }
        
        return null;
    }
    
    /**
     * フィルターをスキップすべきリクエストかどうかを判定
     * 認証が不要なエンドポイント（ログイン、登録など）はフィルターをスキップ
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // 認証不要のエンドポイント
        return path.startsWith("/api/auth/") || 
               path.equals("/error") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/webjars/");
    }
}