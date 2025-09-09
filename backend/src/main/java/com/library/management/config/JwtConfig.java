package com.library.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT設定クラス
 * application.propertiesからJWT関連の設定値を読み込み
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class JwtConfig {
    
    /**
     * JWT署名用秘密鍵
     */
    private String jwtSecret = "myDefaultSecretKeyForLibraryManagementSystem2024";
    
    /**
     * アクセストークン有効期限（ミリ秒）
     * デフォルト: 1時間
     */
    private int jwtExpirationMs = 3600000;
    
    /**
     * リフレッシュトークン有効期限（ミリ秒）
     * デフォルト: 7日間
     */
    private int jwtRefreshExpirationMs = 604800000;
    
    // Getters and Setters
    
    public String getJwtSecret() {
        return jwtSecret;
    }
    
    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }
    
    public int getJwtExpirationMs() {
        return jwtExpirationMs;
    }
    
    public void setJwtExpirationMs(int jwtExpirationMs) {
        this.jwtExpirationMs = jwtExpirationMs;
    }
    
    public int getJwtRefreshExpirationMs() {
        return jwtRefreshExpirationMs;
    }
    
    public void setJwtRefreshExpirationMs(int jwtRefreshExpirationMs) {
        this.jwtRefreshExpirationMs = jwtRefreshExpirationMs;
    }
    
    /**
     * 設定値の妥当性チェック
     * @return 設定値が有効な場合true
     */
    public boolean isValidConfiguration() {
        return jwtSecret != null && 
               !jwtSecret.trim().isEmpty() && 
               jwtSecret.length() >= 32 && // 最低32文字以上
               jwtExpirationMs > 0 && 
               jwtRefreshExpirationMs > 0;
    }
    
    /**
     * 設定値の詳細情報を返す（デバッグ用）
     */
    @Override
    public String toString() {
        return "JwtConfig{" +
                "jwtSecret='***HIDDEN***'" +
                ", jwtExpirationMs=" + jwtExpirationMs + " (" + (jwtExpirationMs / 1000 / 60) + " minutes)" +
                ", jwtRefreshExpirationMs=" + jwtRefreshExpirationMs + " (" + (jwtRefreshExpirationMs / 1000 / 60 / 60 / 24) + " days)" +
                '}';
    }
}