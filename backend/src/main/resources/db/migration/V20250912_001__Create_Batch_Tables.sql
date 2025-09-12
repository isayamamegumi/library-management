-- Spring Batchメタデータテーブル（自動作成されるため、ここでは独自テーブルのみ作成）

-- カスタム統計結果保存テーブル
CREATE TABLE IF NOT EXISTS batch_statistics (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,           -- 'MONTHLY', 'RANKING', 'USER_STATS', 'GENRE_ANALYSIS', 'READING_PACE'
    target_date DATE NOT NULL,                  -- 集計対象日付
    data_json JSONB NOT NULL,                   -- 統計結果（JSON形式）
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(report_type, target_date)
);

-- バッチ実行ログテーブル
CREATE TABLE IF NOT EXISTS batch_execution_logs (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    job_execution_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,               -- 'STARTED', 'COMPLETED', 'FAILED', 'STOPPED'
    exit_code VARCHAR(20),
    exit_message TEXT,
    read_count INTEGER DEFAULT 0,
    write_count INTEGER DEFAULT 0,
    skip_count INTEGER DEFAULT 0,
    error_message TEXT,
    execution_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- システムログテーブル（クリーンアップ対象）
CREATE TABLE IF NOT EXISTS system_logs (
    id BIGSERIAL PRIMARY KEY,
    log_level VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    user_id BIGINT,
    ip_address INET,
    created_at TIMESTAMP DEFAULT NOW()
);

-- ユーザー読書履歴テーブル
CREATE TABLE IF NOT EXISTS user_reading_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    book_id BIGINT NOT NULL REFERENCES books(id),
    status_changed_at TIMESTAMP DEFAULT NOW(),
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- バッチ処理用のインデックス
CREATE INDEX IF NOT EXISTS idx_batch_statistics_type_date ON batch_statistics(report_type, target_date);
CREATE INDEX IF NOT EXISTS idx_batch_execution_logs_job_name ON batch_execution_logs(job_name);
CREATE INDEX IF NOT EXISTS idx_batch_execution_logs_start_time ON batch_execution_logs(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON system_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_user_reading_history_user_id ON user_reading_history(user_id);
CREATE INDEX IF NOT EXISTS idx_user_reading_history_created_at ON user_reading_history(created_at);
CREATE INDEX IF NOT EXISTS idx_books_created_at ON books(created_at);
CREATE INDEX IF NOT EXISTS idx_books_user_status ON books(user_id, read_status_id);

-- テストデータ用システムログ
INSERT INTO system_logs (log_level, message, user_id) 
SELECT 
    CASE 
        WHEN random() < 0.1 THEN 'ERROR'
        WHEN random() < 0.3 THEN 'WARN'
        ELSE 'INFO'
    END as log_level,
    'システムログメッセージ ' || generate_series as message,
    CASE WHEN random() < 0.7 THEN floor(random() * 10)::bigint + 1 ELSE NULL END as user_id
FROM generate_series(1, 1000);