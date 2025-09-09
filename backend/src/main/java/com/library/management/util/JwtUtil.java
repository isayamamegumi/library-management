package com.library.management.util;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;

/**
 * JWT（JSON Web Token）ユーティリティクラス
 * トークンの生成、検証、情報抽出を担当
 */
@Component
public class JwtUtil {
    
    @Value("${app.jwtSecret:myDefaultSecretKeyForLibraryManagementSystem2024_EXTENDED_FOR_HS512_ALGORITHM_MINIMUM_LENGTH_REQUIREMENT}")
    private String jwtSecret;
    
    @Value("${app.jwtExpirationMs:3600000}") // 1時間 = 3600000ms
    private int jwtExpirationMs;
    
    @Value("${app.jwtRefreshExpirationMs:604800000}") // 7日間 = 604800000ms
    private int jwtRefreshExpirationMs;
    
    /**
     * 署名用のキーを取得
     */
    private Key getSignInKey() {
        byte[] keyBytes = jwtSecret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * アクセストークンを生成
     * @param userDetails ユーザー情報
     * @return JWTトークン
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // ロール情報を含める
        claims.put("authorities", userDetails.getAuthorities());
        return createToken(claims, userDetails.getUsername(), jwtExpirationMs);
    }

    /**
     * アクセストークンを生成（User エンティティ用）
     * @param user ユーザーエンティティ
     * @return JWTトークン
     */
    public String generateAccessToken(com.library.management.entity.User user) {
        Map<String, Object> claims = new HashMap<>();
        // ロール情報を含める（ROLE_プレフィックス付き）
        claims.put("authorities", "[{\"authority\":\"ROLE_" + user.getRole().getName().toUpperCase() + "\"}]");
        claims.put("role", user.getRole().getName());
        return createToken(claims, user.getUsername(), jwtExpirationMs);
    }
    
    /**
     * リフレッシュトークンを生成
     * @param username ユーザー名
     * @return リフレッシュトークン
     */
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "REFRESH");
        return createToken(claims, username, jwtRefreshExpirationMs);
    }
    
    /**
     * トークン生成の共通処理
     * @param claims クレーム情報
     * @param subject 主体（ユーザー名）
     * @param expiration 有効期限（ミリ秒）
     * @return JWTトークン
     */
    private String createToken(Map<String, Object> claims, String subject, int expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * トークンからユーザー名を抽出
     * @param token JWTトークン
     * @return ユーザー名
     */
    public String getUsernameFromJwtToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }
    
    /**
     * トークンから有効期限を抽出
     * @param token JWTトークン
     * @return 有効期限
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }
    
    /**
     * トークンから特定のクレームを取得
     * @param token JWTトークン
     * @param claimsResolver クレーム取得関数
     * @param <T> 戻り値の型
     * @return クレーム値
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * トークンからすべてのクレームを取得
     * @param token JWTトークン
     * @return すべてのクレーム
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * トークンの有効期限切れチェック
     * @param token JWTトークン
     * @return 有効期限切れの場合true
     */
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
    
    /**
     * トークンの検証
     * @param token JWTトークン
     * @param userDetails ユーザー情報
     * @return 有効な場合true
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromJwtToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    /**
     * JWTトークンの形式・署名検証
     * @param authToken JWTトークン
     * @return 有効な場合true
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(authToken);
            return true;
        } catch (SecurityException e) {
            System.err.println("Invalid JWT signature: " + e.getMessage());
        } catch (MalformedJwtException e) {
            System.err.println("Invalid JWT token: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            System.err.println("JWT token is expired: " + e.getMessage());
        } catch (UnsupportedJwtException e) {
            System.err.println("JWT token is unsupported: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("JWT claims string is empty: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * リフレッシュトークンかどうかを判定
     * @param token JWTトークン
     * @return リフレッシュトークンの場合true
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return "REFRESH".equals(claims.get("tokenType"));
        } catch (Exception e) {
            return false;
        }
    }
}