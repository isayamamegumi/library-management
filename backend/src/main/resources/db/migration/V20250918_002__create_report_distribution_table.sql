-- レポート配信設定テーブル作成
CREATE TABLE report_distributions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    schedule_id BIGINT NULL,
    distribution_type VARCHAR(50) NOT NULL,
    recipients TEXT NOT NULL,
    distribution_config TEXT NULL,
    subject VARCHAR(200) NULL,
    message TEXT NULL,
    attach_file BOOLEAN DEFAULT TRUE,
    compress_file BOOLEAN DEFAULT FALSE,
    password_protection VARCHAR(100) NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    last_distribution_time TIMESTAMP NULL,
    distribution_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- インデックス作成
CREATE INDEX idx_report_distributions_user_id ON report_distributions(user_id);
CREATE INDEX idx_report_distributions_schedule_id ON report_distributions(schedule_id);
CREATE INDEX idx_report_distributions_type ON report_distributions(distribution_type);
CREATE INDEX idx_report_distributions_status ON report_distributions(status);
CREATE INDEX idx_report_distributions_active ON report_distributions(is_active);
CREATE INDEX idx_report_distributions_user_name ON report_distributions(user_id, name);

-- PostgreSQL用コメント追加
COMMENT ON TABLE report_distributions IS 'レポート自動配信設定テーブル';