-- 帳票キャッシュテーブル作成
CREATE TABLE report_cache (
    id BIGSERIAL PRIMARY KEY,
    cache_key VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    format VARCHAR(20) NOT NULL,
    parameters TEXT,
    file_path VARCHAR(500),
    file_size_bytes BIGINT,
    record_count INT,
    generation_time_ms BIGINT,
    hit_count INT DEFAULT 0,
    last_access_time TIMESTAMP,
    expires_at TIMESTAMP,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    cache_status VARCHAR(50),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- インデックス作成
CREATE INDEX idx_cache_key ON report_cache(cache_key);
CREATE INDEX idx_user_id ON report_cache(user_id);
CREATE INDEX idx_report_type ON report_cache(report_type);
CREATE INDEX idx_cache_lookup ON report_cache(user_id, report_type, format, is_valid);
CREATE INDEX idx_expires_at ON report_cache(expires_at);
CREATE INDEX idx_last_access_time ON report_cache(last_access_time);
CREATE INDEX idx_is_valid ON report_cache(is_valid);
CREATE INDEX idx_cache_status ON report_cache(cache_status);

-- 一意制約
ALTER TABLE report_cache ADD CONSTRAINT uk_cache_key UNIQUE (cache_key);

-- PostgreSQL用コメント追加
COMMENT ON TABLE report_cache IS '帳票キャッシュテーブル';
COMMENT ON COLUMN report_cache.cache_key IS 'キャッシュキー';
COMMENT ON COLUMN report_cache.user_id IS 'ユーザーID';
COMMENT ON COLUMN report_cache.report_type IS 'レポートタイプ';
COMMENT ON COLUMN report_cache.format IS '出力フォーマット';
COMMENT ON COLUMN report_cache.parameters IS 'パラメータ（JSON）';
COMMENT ON COLUMN report_cache.file_path IS 'ファイルパス';
COMMENT ON COLUMN report_cache.file_size_bytes IS 'ファイルサイズ（バイト）';
COMMENT ON COLUMN report_cache.record_count IS 'レコード数';
COMMENT ON COLUMN report_cache.generation_time_ms IS '生成時間（ミリ秒）';
COMMENT ON COLUMN report_cache.hit_count IS 'ヒット回数';
COMMENT ON COLUMN report_cache.last_access_time IS '最終アクセス時刻';
COMMENT ON COLUMN report_cache.expires_at IS '有効期限';
COMMENT ON COLUMN report_cache.is_valid IS '有効フラグ';
COMMENT ON COLUMN report_cache.cache_status IS 'キャッシュステータス';
COMMENT ON COLUMN report_cache.metadata IS 'メタデータ（JSON）';
COMMENT ON COLUMN report_cache.created_at IS '作成日時';
COMMENT ON COLUMN report_cache.updated_at IS '更新日時';