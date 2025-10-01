-- レポート権限管理テーブル作成
CREATE TABLE report_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id BIGINT NULL,
    permission VARCHAR(50) NOT NULL,
    access_level VARCHAR(50) NOT NULL,
    conditions TEXT NULL,
    expires_at TIMESTAMP NULL,
    granted_by BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- インデックス作成
CREATE INDEX idx_report_permissions_user_id ON report_permissions(user_id);
CREATE INDEX idx_report_permissions_resource ON report_permissions(resource_type, resource_id);
CREATE INDEX idx_report_permissions_permission ON report_permissions(permission);
CREATE INDEX idx_report_permissions_expires_at ON report_permissions(expires_at);
CREATE INDEX idx_report_permissions_active ON report_permissions(is_active);
CREATE INDEX idx_report_permissions_granted_by ON report_permissions(granted_by);
CREATE INDEX idx_report_permissions_user_resource ON report_permissions(user_id, resource_type, resource_id);

-- 複合インデックス（高速権限チェック用）
CREATE INDEX idx_report_permissions_check ON report_permissions(user_id, resource_type, resource_id, permission, is_active, expires_at);

-- PostgreSQL用コメント追加
COMMENT ON TABLE report_permissions IS 'レポート機能アクセス権限管理テーブル';

-- サンプルデータ投入（管理者権限）
INSERT INTO report_permissions (user_id, resource_type, resource_id, permission, access_level, granted_by, is_active)
VALUES
(1, 'SYSTEM', NULL, 'ADMIN', 'ALL', 1, TRUE),
(1, 'TEMPLATE', NULL, 'ADMIN', 'ALL', 1, TRUE),
(1, 'SCHEDULE', NULL, 'ADMIN', 'ALL', 1, TRUE),
(1, 'DISTRIBUTION', NULL, 'ADMIN', 'ALL', 1, TRUE),
(1, 'REPORT', NULL, 'ADMIN', 'ALL', 1, TRUE);