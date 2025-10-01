package com.library.management.service.report.security;

import com.library.management.entity.ReportPermission;
import com.library.management.repository.ReportPermissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 帳票アクセス制御サービス
 */
@Service
@Transactional
public class ReportAccessControlService {

    private static final Logger logger = LoggerFactory.getLogger(ReportAccessControlService.class);

    @Autowired
    private ReportPermissionRepository permissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // 権限定数
    public static final String PERMISSION_READ = "READ";
    public static final String PERMISSION_WRITE = "WRITE";
    public static final String PERMISSION_EXECUTE = "EXECUTE";
    public static final String PERMISSION_DELETE = "DELETE";
    public static final String PERMISSION_ADMIN = "ADMIN";

    // リソースタイプ定数
    public static final String RESOURCE_TEMPLATE = "TEMPLATE";
    public static final String RESOURCE_SCHEDULE = "SCHEDULE";
    public static final String RESOURCE_DISTRIBUTION = "DISTRIBUTION";
    public static final String RESOURCE_REPORT = "REPORT";
    public static final String RESOURCE_SYSTEM = "SYSTEM";

    // アクセスレベル定数
    public static final String ACCESS_LEVEL_OWN = "OWN";
    public static final String ACCESS_LEVEL_GROUP = "GROUP";
    public static final String ACCESS_LEVEL_ALL = "ALL";

    /**
     * 権限チェック
     */
    public boolean hasPermission(Long userId, String resourceType, Long resourceId, String permission) {
        try {
            logger.debug("権限チェック開始: userId={}, resourceType={}, resourceId={}, permission={}",
                userId, resourceType, resourceId, permission);

            LocalDateTime currentTime = LocalDateTime.now();

            // 有効な権限を検索
            Optional<ReportPermission> validPermission = permissionRepository.findValidPermission(
                userId, resourceType, resourceId, permission, currentTime);

            if (validPermission.isPresent()) {
                // 条件チェック
                boolean conditionMet = checkPermissionConditions(validPermission.get(), userId, resourceId);
                logger.debug("権限チェック結果: hasPermission={}, conditionMet={}", true, conditionMet);
                return conditionMet;
            }

            // リソース固有の権限がない場合、全体権限をチェック
            Optional<ReportPermission> globalPermission = permissionRepository.findValidPermission(
                userId, resourceType, null, permission, currentTime);

            if (globalPermission.isPresent()) {
                boolean conditionMet = checkPermissionConditions(globalPermission.get(), userId, resourceId);
                logger.debug("権限チェック結果: hasGlobalPermission={}, conditionMet={}", true, conditionMet);
                return conditionMet;
            }

            logger.debug("権限チェック結果: hasPermission=false");
            return false;

        } catch (Exception e) {
            logger.error("権限チェックエラー: userId={}, resourceType={}, resourceId={}, permission={}",
                userId, resourceType, resourceId, permission, e);
            return false;
        }
    }

    /**
     * 管理者権限チェック
     */
    public boolean isAdmin(Long userId) {
        return hasPermission(userId, RESOURCE_SYSTEM, null, PERMISSION_ADMIN);
    }

    /**
     * ユーザーの全権限取得
     */
    public List<String> getUserPermissions(Long userId, String resourceType, Long resourceId) {
        try {
            LocalDateTime currentTime = LocalDateTime.now();
            List<String> permissions = permissionRepository.findUserPermissionNames(
                userId, resourceType, resourceId, currentTime);

            logger.debug("ユーザー権限取得: userId={}, resourceType={}, resourceId={}, permissions={}",
                userId, resourceType, resourceId, permissions);

            return permissions;

        } catch (Exception e) {
            logger.error("ユーザー権限取得エラー: userId={}, resourceType={}, resourceId={}",
                userId, resourceType, resourceId, e);
            return List.of();
        }
    }

    /**
     * 権限付与
     */
    public ReportPermission grantPermission(PermissionGrantRequest request, Long grantedBy) {
        try {
            logger.info("権限付与開始: userId={}, resourceType={}, resourceId={}, permission={}, grantedBy={}",
                request.getUserId(), request.getResourceType(), request.getResourceId(),
                request.getPermission(), grantedBy);

            // 権限付与者の権限チェック
            if (!canGrantPermission(grantedBy, request.getResourceType(), request.getResourceId(), request.getPermission())) {
                throw new SecurityException("権限付与の権限がありません");
            }

            // 既存権限チェック
            Optional<ReportPermission> existing = permissionRepository.findExistingPermission(
                request.getUserId(), request.getResourceType(),
                request.getResourceId(), request.getPermission());

            if (existing.isPresent()) {
                throw new IllegalArgumentException("既に同じ権限が付与されています");
            }

            // 権限作成
            ReportPermission permission = new ReportPermission(
                request.getUserId(),
                request.getResourceType(),
                request.getResourceId(),
                request.getPermission(),
                request.getAccessLevel(),
                grantedBy
            );

            permission.setExpiresAt(request.getExpiresAt());
            permission.setConditions(request.getConditions() != null ?
                objectMapper.writeValueAsString(request.getConditions()) : null);

            ReportPermission savedPermission = permissionRepository.save(permission);

            logger.info("権限付与完了: permissionId={}, userId={}, permission={}",
                savedPermission.getId(), request.getUserId(), request.getPermission());

            return savedPermission;

        } catch (Exception e) {
            logger.error("権限付与エラー: userId={}, resourceType={}, permission={}",
                request.getUserId(), request.getResourceType(), request.getPermission(), e);
            throw new RuntimeException("権限付与に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 権限取り消し
     */
    public void revokePermission(Long permissionId, Long revokedBy) {
        try {
            Optional<ReportPermission> permissionOpt = permissionRepository.findById(permissionId);
            if (permissionOpt.isEmpty()) {
                throw new IllegalArgumentException("権限が見つかりません: " + permissionId);
            }

            ReportPermission permission = permissionOpt.get();

            // 権限取り消し者の権限チェック
            if (!canRevokePermission(revokedBy, permission)) {
                throw new SecurityException("権限取り消しの権限がありません");
            }

            // 論理削除
            permission.setIsActive(false);
            permissionRepository.save(permission);

            logger.info("権限取り消し完了: permissionId={}, userId={}, revokedBy={}",
                permissionId, permission.getUserId(), revokedBy);

        } catch (Exception e) {
            logger.error("権限取り消しエラー: permissionId={}, revokedBy={}", permissionId, revokedBy, e);
            throw new RuntimeException("権限取り消しに失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 期限切れ権限の自動削除
     */
    @Transactional
    public void cleanupExpiredPermissions() {
        try {
            logger.info("期限切れ権限削除開始");

            LocalDateTime currentTime = LocalDateTime.now();
            List<ReportPermission> expiredPermissions = permissionRepository.findExpiredPermissions(currentTime);

            if (expiredPermissions.isEmpty()) {
                logger.info("期限切れ権限なし");
                return;
            }

            // 期限切れ権限を非活性化
            for (ReportPermission permission : expiredPermissions) {
                permission.setIsActive(false);
            }

            permissionRepository.saveAll(expiredPermissions);

            logger.info("期限切れ権限削除完了: count={}", expiredPermissions.size());

        } catch (Exception e) {
            logger.error("期限切れ権限削除エラー", e);
        }
    }

    /**
     * リソースアクセス可能性チェック
     */
    public boolean canAccessResource(Long userId, String resourceType, Long resourceId) {
        // 基本的な読み取り権限をチェック
        return hasPermission(userId, resourceType, resourceId, PERMISSION_READ) ||
               hasPermission(userId, resourceType, resourceId, PERMISSION_WRITE) ||
               hasPermission(userId, resourceType, resourceId, PERMISSION_ADMIN);
    }

    /**
     * リソース変更可能性チェック
     */
    public boolean canModifyResource(Long userId, String resourceType, Long resourceId) {
        return hasPermission(userId, resourceType, resourceId, PERMISSION_WRITE) ||
               hasPermission(userId, resourceType, resourceId, PERMISSION_ADMIN);
    }

    /**
     * リソース削除可能性チェック
     */
    public boolean canDeleteResource(Long userId, String resourceType, Long resourceId) {
        return hasPermission(userId, resourceType, resourceId, PERMISSION_DELETE) ||
               hasPermission(userId, resourceType, resourceId, PERMISSION_ADMIN);
    }

    /**
     * 権限条件チェック
     */
    @SuppressWarnings("unchecked")
    private boolean checkPermissionConditions(ReportPermission permission, Long userId, Long resourceId) {
        try {
            if (permission.getConditions() == null || permission.getConditions().trim().isEmpty()) {
                return true;
            }

            Map<String, Object> conditions = objectMapper.readValue(permission.getConditions(), Map.class);

            // 時間制限チェック
            if (conditions.containsKey("timeRestriction")) {
                Map<String, Object> timeRestriction = (Map<String, Object>) conditions.get("timeRestriction");
                if (!checkTimeRestriction(timeRestriction)) {
                    return false;
                }
            }

            // IP制限チェック（簡易実装）
            if (conditions.containsKey("ipRestriction")) {
                List<String> allowedIps = (List<String>) conditions.get("ipRestriction");
                if (!checkIpRestriction(allowedIps)) {
                    return false;
                }
            }

            // カスタム条件チェック
            if (conditions.containsKey("customCondition")) {
                Map<String, Object> customCondition = (Map<String, Object>) conditions.get("customCondition");
                if (!checkCustomCondition(customCondition, userId, resourceId)) {
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            logger.error("権限条件チェックエラー: permissionId={}", permission.getId(), e);
            return false;
        }
    }

    /**
     * 時間制限チェック
     */
    private boolean checkTimeRestriction(Map<String, Object> timeRestriction) {
        // 簡易実装：営業時間チェック
        Integer startHour = (Integer) timeRestriction.get("startHour");
        Integer endHour = (Integer) timeRestriction.get("endHour");

        if (startHour != null && endHour != null) {
            int currentHour = LocalDateTime.now().getHour();
            return currentHour >= startHour && currentHour <= endHour;
        }

        return true;
    }

    /**
     * IP制限チェック
     */
    private boolean checkIpRestriction(List<String> allowedIps) {
        // 簡易実装：実際にはリクエストのIPアドレスを取得して比較
        logger.debug("IP制限チェック: allowedIps={}", allowedIps);
        return true; // 実装省略
    }

    /**
     * カスタム条件チェック
     */
    private boolean checkCustomCondition(Map<String, Object> customCondition, Long userId, Long resourceId) {
        // カスタムビジネスロジックの実装場所
        logger.debug("カスタム条件チェック: condition={}, userId={}, resourceId={}",
            customCondition, userId, resourceId);
        return true; // 実装省略
    }

    /**
     * 権限付与可能性チェック
     */
    private boolean canGrantPermission(Long grantedBy, String resourceType, Long resourceId, String permission) {
        // 管理者権限または該当リソースの管理権限をチェック
        return isAdmin(grantedBy) ||
               hasPermission(grantedBy, resourceType, resourceId, PERMISSION_ADMIN);
    }

    /**
     * 権限取り消し可能性チェック
     */
    private boolean canRevokePermission(Long revokedBy, ReportPermission permission) {
        // 管理者権限、権限付与者、または該当リソースの管理権限をチェック
        return isAdmin(revokedBy) ||
               revokedBy.equals(permission.getGrantedBy()) ||
               hasPermission(revokedBy, permission.getResourceType(), permission.getResourceId(), PERMISSION_ADMIN);
    }

    /**
     * 権限付与リクエスト
     */
    public static class PermissionGrantRequest {
        private Long userId;
        private String resourceType;
        private Long resourceId;
        private String permission;
        private String accessLevel;
        private Map<String, Object> conditions;
        private LocalDateTime expiresAt;

        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }

        public String getAccessLevel() { return accessLevel; }
        public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

        public Map<String, Object> getConditions() { return conditions; }
        public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }

        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
}