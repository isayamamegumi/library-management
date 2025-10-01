package com.library.management.service.report.schedule;

import com.library.management.entity.ReportSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * レポートスケジュール実行器
 * 定期的にスケジュールをチェックして実行する
 */
@Component
public class ReportScheduleExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReportScheduleExecutor.class);

    @Autowired
    private ReportScheduleService scheduleService;

    /**
     * スケジュール定期実行
     * 1分ごとに実行対象をチェック
     */
    @Scheduled(fixedRate = 60000) // 60秒間隔
    public void executeScheduledReports() {
        try {
            logger.debug("スケジュール実行チェック開始: {}", LocalDateTime.now());

            List<ReportSchedule> schedulesToRun = scheduleService.getSchedulesToRun();

            if (schedulesToRun.isEmpty()) {
                logger.debug("実行対象スケジュールなし");
                return;
            }

            logger.info("実行対象スケジュール発見: count={}", schedulesToRun.size());

            // 並行実行
            List<CompletableFuture<Void>> futures = schedulesToRun.stream()
                .map(schedule -> {
                    logger.info("スケジュール実行開始: scheduleId={}, name={}, userId={}",
                        schedule.getId(), schedule.getName(), schedule.getUserId());
                    return scheduleService.executeSchedule(schedule);
                })
                .toList();

            // 全ての実行完了を待機
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> logger.info("スケジュール一括実行完了: count={}", schedulesToRun.size()))
                .exceptionally(throwable -> {
                    logger.error("スケジュール一括実行中にエラー発生", throwable);
                    return null;
                });

        } catch (Exception e) {
            logger.error("スケジュール定期実行エラー", e);
        }
    }

    /**
     * 初回実行対象スケジュールチェック
     * 1時間ごとに未実行スケジュールをチェック
     */
    @Scheduled(fixedRate = 3600000) // 1時間間隔
    public void checkNeverRunSchedules() {
        try {
            logger.debug("未実行スケジュールチェック開始");

            List<ReportSchedule> neverRunSchedules = scheduleService.findNeverRunSchedules();

            if (neverRunSchedules.isEmpty()) {
                logger.debug("未実行スケジュールなし");
                return;
            }

            logger.info("未実行スケジュール発見: count={}", neverRunSchedules.size());

            // 次回実行時刻が過去の場合は即座に実行
            LocalDateTime now = LocalDateTime.now();
            for (ReportSchedule schedule : neverRunSchedules) {
                if (schedule.getNextRunTime() != null && schedule.getNextRunTime().isBefore(now)) {
                    logger.info("未実行スケジュールの即座実行: scheduleId={}, name={}",
                        schedule.getId(), schedule.getName());
                    scheduleService.executeSchedule(schedule);
                }
            }

        } catch (Exception e) {
            logger.error("未実行スケジュールチェックエラー", e);
        }
    }

    /**
     * エラー状態スケジュールの監視
     * 6時間ごとにエラー状態のスケジュールをチェック
     */
    @Scheduled(fixedRate = 21600000) // 6時間間隔
    public void monitorErrorSchedules() {
        try {
            logger.debug("エラー状態スケジュール監視開始");

            List<ReportSchedule> errorSchedules = scheduleService.findByStatusAndIsActiveTrue("ERROR");

            if (errorSchedules.isEmpty()) {
                logger.debug("エラー状態スケジュールなし");
                return;
            }

            logger.warn("エラー状態スケジュール発見: count={}", errorSchedules.size());

            // エラー状態のスケジュールをログ出力
            for (ReportSchedule schedule : errorSchedules) {
                logger.warn("エラー状態スケジュール: scheduleId={}, name={}, userId={}, lastRunTime={}",
                    schedule.getId(), schedule.getName(), schedule.getUserId(), schedule.getLastRunTime());
            }

            // TODO: 管理者への通知機能を追加

        } catch (Exception e) {
            logger.error("エラー状態スケジュール監視エラー", e);
        }
    }

    /**
     * スケジュール統計情報出力
     * 1日1回（午前0時）に統計情報をログ出力
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void outputScheduleStatistics() {
        try {
            logger.info("スケジュール統計情報出力開始");

            // 全体統計
            long totalSchedules = scheduleService.count();
            long activeSchedules = scheduleService.findByStatusAndIsActiveTrue("ACTIVE").size();
            long errorSchedules = scheduleService.findByStatusAndIsActiveTrue("ERROR").size();

            logger.info("スケジュール統計: 総数={}, アクティブ={}, エラー={}",
                totalSchedules, activeSchedules, errorSchedules);

            // スケジュールタイプ別統計
            List<ReportSchedule> dailySchedules = scheduleService.findByScheduleTypeAndActive("DAILY");
            List<ReportSchedule> weeklySchedules = scheduleService.findByScheduleTypeAndActive("WEEKLY");
            List<ReportSchedule> monthlySchedules = scheduleService.findByScheduleTypeAndActive("MONTHLY");

            logger.info("スケジュールタイプ別統計: 日次={}, 週次={}, 月次={}",
                dailySchedules.size(), weeklySchedules.size(), monthlySchedules.size());

        } catch (Exception e) {
            logger.error("スケジュール統計情報出力エラー", e);
        }
    }
}