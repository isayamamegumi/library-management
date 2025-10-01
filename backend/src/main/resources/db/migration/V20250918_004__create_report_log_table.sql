-- レポート生成ログテーブル作成
CREATE TABLE report_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NULL,
    report_type VARCHAR(50) NOT NULL,
    format VARCHAR(20) NOT NULL,
    template_id BIGINT NULL,
    schedule_id BIGINT NULL,
    distribution_id BIGINT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    processing_time_ms BIGINT NULL,
    record_count INT NULL,
    file_size_bytes BIGINT NULL,
    file_name VARCHAR(500) NULL,
    file_path VARCHAR(500) NULL,
    parameters TEXT NULL,
    error_message TEXT NULL,
    error_stack_trace TEXT NULL,
    execution_context VARCHAR(100) NULL,
    client_ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    additional_info TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- インデックス作成
CREATE INDEX idx_report_logs_user_id ON report_logs(user_id);
CREATE INDEX idx_report_logs_status ON report_logs(status);
CREATE INDEX idx_report_logs_report_type ON report_logs(report_type);
CREATE INDEX idx_report_logs_created_at ON report_logs(created_at);
CREATE INDEX idx_report_logs_start_time ON report_logs(start_time);
CREATE INDEX idx_report_logs_end_time ON report_logs(end_time);
CREATE INDEX idx_report_logs_template_id ON report_logs(template_id);
CREATE INDEX idx_report_logs_schedule_id ON report_logs(schedule_id);
CREATE INDEX idx_report_logs_execution_context ON report_logs(execution_context);

-- 複合インデックス（統計用）
CREATE INDEX idx_report_logs_stats ON report_logs(status, created_at, report_type);
CREATE INDEX idx_report_logs_user_stats ON report_logs(user_id, status, created_at);
CREATE INDEX idx_report_logs_date_range ON report_logs(created_at, status);

-- PostgreSQL用コメント追加
COMMENT ON TABLE report_logs IS 'レポート生成実行ログテーブル';
COMMENT ON COLUMN report_logs.user_id IS 'ユーザーID';
COMMENT ON COLUMN report_logs.username IS 'ユーザー名';
COMMENT ON COLUMN report_logs.report_type IS 'レポートタイプ';
COMMENT ON COLUMN report_logs.format IS '出力フォーマット';
COMMENT ON COLUMN report_logs.template_id IS 'テンプレートID';
COMMENT ON COLUMN report_logs.schedule_id IS 'スケジュールID';
COMMENT ON COLUMN report_logs.distribution_id IS '配信設定ID';
COMMENT ON COLUMN report_logs.status IS 'ステータス (STARTED, SUCCESS, ERROR)';
COMMENT ON COLUMN report_logs.start_time IS '開始時刻';
COMMENT ON COLUMN report_logs.end_time IS '終了時刻';
COMMENT ON COLUMN report_logs.processing_time_ms IS '処理時間（ミリ秒）';
COMMENT ON COLUMN report_logs.record_count IS 'レコード数';
COMMENT ON COLUMN report_logs.file_size_bytes IS 'ファイルサイズ（バイト）';
COMMENT ON COLUMN report_logs.file_name IS 'ファイル名';
COMMENT ON COLUMN report_logs.file_path IS 'ファイルパス';
COMMENT ON COLUMN report_logs.parameters IS 'パラメータ（JSON）';
COMMENT ON COLUMN report_logs.error_message IS 'エラーメッセージ';
COMMENT ON COLUMN report_logs.error_stack_trace IS 'エラースタックトレース';
COMMENT ON COLUMN report_logs.execution_context IS '実行コンテキスト (MANUAL, SCHEDULED)';
COMMENT ON COLUMN report_logs.client_ip_address IS 'クライアントIPアドレス';
COMMENT ON COLUMN report_logs.user_agent IS 'ユーザーエージェント';
COMMENT ON COLUMN report_logs.additional_info IS '追加情報（JSON）';
COMMENT ON COLUMN report_logs.created_at IS '作成日時';