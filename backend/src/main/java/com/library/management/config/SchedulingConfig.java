package com.library.management.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * スケジューリング設定
 * 帳票のクリーンアップタスクなどのスケジュール実行を有効化
 * 非同期処理も有効化
 */
@Configuration
@EnableScheduling
@EnableAsync
@ConditionalOnProperty(name = "app.reports.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
    // @EnableSchedulingアノテーションにより、@Scheduledアノテーションが有効になる
    // @EnableAsyncアノテーションにより、@Asyncアノテーションが有効になる
    // スケジュールタスクはReportFileServiceで定義済み
}