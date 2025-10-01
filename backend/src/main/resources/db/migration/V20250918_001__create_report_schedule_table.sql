-- レポートスケジュールテーブル作成
CREATE TABLE report_schedules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    template_id BIGINT NULL,
    report_type VARCHAR(50) NOT NULL,
    format VARCHAR(20) NOT NULL,
    report_filters TEXT NULL,
    report_options TEXT NULL,
    schedule_type VARCHAR(50) NOT NULL,
    schedule_config TEXT NOT NULL,
    next_run_time TIMESTAMP NULL,
    last_run_time TIMESTAMP NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    output_config TEXT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- インデックス作成
CREATE INDEX idx_report_schedules_user_id ON report_schedules(user_id);
CREATE INDEX idx_report_schedules_next_run_time ON report_schedules(next_run_time);
CREATE INDEX idx_report_schedules_status ON report_schedules(status);
CREATE INDEX idx_report_schedules_schedule_type ON report_schedules(schedule_type);
CREATE INDEX idx_report_schedules_active ON report_schedules(is_active);
CREATE INDEX idx_report_schedules_user_name ON report_schedules(user_id, name);

-- PostgreSQL用コメント追加
COMMENT ON TABLE report_schedules IS 'レポート出力スケジュール管理テーブル';
COMMENT ON COLUMN report_schedules.name IS 'スケジュール名';
COMMENT ON COLUMN report_schedules.user_id IS 'ユーザーID';
COMMENT ON COLUMN report_schedules.template_id IS 'テンプレートID';
COMMENT ON COLUMN report_schedules.report_type IS 'レポートタイプ';
COMMENT ON COLUMN report_schedules.format IS '出力フォーマット';
COMMENT ON COLUMN report_schedules.report_filters IS 'レポートフィルター（JSON）';
COMMENT ON COLUMN report_schedules.report_options IS 'レポートオプション（JSON）';
COMMENT ON COLUMN report_schedules.schedule_type IS 'スケジュールタイプ';
COMMENT ON COLUMN report_schedules.schedule_config IS 'スケジュール設定（JSON）';
COMMENT ON COLUMN report_schedules.next_run_time IS '次回実行時刻';
COMMENT ON COLUMN report_schedules.last_run_time IS '最終実行時刻';
COMMENT ON COLUMN report_schedules.status IS 'ステータス';
COMMENT ON COLUMN report_schedules.output_config IS '出力設定（JSON）';
COMMENT ON COLUMN report_schedules.is_active IS 'アクティブフラグ';
COMMENT ON COLUMN report_schedules.created_at IS '作成日時';
COMMENT ON COLUMN report_schedules.updated_at IS '更新日時';