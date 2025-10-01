-- Create report_history table for storing report generation history
CREATE TABLE IF NOT EXISTS report_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    format VARCHAR(10) NOT NULL,
    parameters TEXT,
    file_path VARCHAR(500),
    file_size BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,

    CONSTRAINT fk_report_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_report_history_user_id ON report_history(user_id);
CREATE INDEX IF NOT EXISTS idx_report_history_status ON report_history(status);
CREATE INDEX IF NOT EXISTS idx_report_history_created_at ON report_history(created_at);
CREATE INDEX IF NOT EXISTS idx_report_history_expires_at ON report_history(expires_at);

-- Add comments
COMMENT ON TABLE report_history IS '帳票生成履歴テーブル';
COMMENT ON COLUMN report_history.user_id IS 'ユーザーID';
COMMENT ON COLUMN report_history.report_type IS 'レポートタイプ（personal, system, book_list等）';
COMMENT ON COLUMN report_history.format IS '出力フォーマット（PDF, EXCEL等）';
COMMENT ON COLUMN report_history.parameters IS 'リクエストパラメータ（JSON形式）';
COMMENT ON COLUMN report_history.file_path IS '生成ファイルのパス';
COMMENT ON COLUMN report_history.file_size IS 'ファイルサイズ（バイト）';
COMMENT ON COLUMN report_history.status IS 'ステータス（GENERATING, COMPLETED, FAILED）';
COMMENT ON COLUMN report_history.created_at IS '作成日時';
COMMENT ON COLUMN report_history.expires_at IS '有効期限';