package com.library.management.service.report.schedule;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.ReportSchedule;
import com.library.management.repository.ReportScheduleRepository;
import com.library.management.service.report.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 帳票スケジュール管理サービス
 */
@Service
@Transactional
public class ReportScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(ReportScheduleService.class);
    private static final int MAX_SCHEDULES_PER_USER = 50;

    @Autowired
    protected ReportScheduleRepository scheduleRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * スケジュール作成
     */
    public ReportSchedule createSchedule(ScheduleCreateRequest request, Long userId) {
        try {
            logger.info("スケジュール作成開始: name={}, userId={}, scheduleType={}",
                request.getName(), userId, request.getScheduleType());

            // 制限チェック
            validateScheduleCreation(request, userId);

            // スケジュール設定
            ReportSchedule schedule = new ReportSchedule(
                request.getName(),
                userId,
                request.getReportType(),
                request.getFormat(),
                request.getScheduleType(),
                objectMapper.writeValueAsString(request.getScheduleConfig())
            );

            schedule.setTemplateId(request.getTemplateId());
            schedule.setReportFilters(objectMapper.writeValueAsString(request.getReportFilters()));
            schedule.setReportOptions(objectMapper.writeValueAsString(request.getReportOptions()));
            schedule.setOutputConfig(objectMapper.writeValueAsString(request.getOutputConfig()));

            // 次回実行時刻計算
            LocalDateTime nextRunTime = calculateNextRunTime(request.getScheduleConfig(), request.getScheduleType());
            schedule.setNextRunTime(nextRunTime);

            ReportSchedule savedSchedule = scheduleRepository.save(schedule);
            logger.info("スケジュール作成完了: scheduleId={}, nextRunTime={}", savedSchedule.getId(), nextRunTime);

            return savedSchedule;

        } catch (Exception e) {
            logger.error("スケジュール作成エラー: name={}, userId={}", request.getName(), userId, e);
            throw new RuntimeException("スケジュールの作成に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * スケジュール更新
     */
    public ReportSchedule updateSchedule(Long scheduleId, ScheduleUpdateRequest request, Long userId) {
        try {
            Optional<ReportSchedule> scheduleOpt = scheduleRepository.findById(scheduleId);
            if (scheduleOpt.isEmpty()) {
                throw new IllegalArgumentException("スケジュールが見つかりません: " + scheduleId);
            }

            ReportSchedule schedule = scheduleOpt.get();

            // 権限チェック
            if (!userId.equals(schedule.getUserId())) {
                throw new IllegalArgumentException("スケジュールの更新権限がありません");
            }

            // 更新処理
            if (request.getName() != null) {
                schedule.setName(request.getName());
            }

            if (request.getScheduleConfig() != null) {
                schedule.setScheduleConfig(objectMapper.writeValueAsString(request.getScheduleConfig()));
                // 次回実行時刻再計算
                LocalDateTime nextRunTime = calculateNextRunTime(request.getScheduleConfig(), schedule.getScheduleType());
                schedule.setNextRunTime(nextRunTime);
            }

            if (request.getReportFilters() != null) {
                schedule.setReportFilters(objectMapper.writeValueAsString(request.getReportFilters()));
            }

            if (request.getOutputConfig() != null) {
                schedule.setOutputConfig(objectMapper.writeValueAsString(request.getOutputConfig()));
            }

            if (request.getStatus() != null) {
                schedule.setStatus(request.getStatus());
            }

            ReportSchedule updatedSchedule = scheduleRepository.save(schedule);
            logger.info("スケジュール更新完了: scheduleId={}, userId={}", scheduleId, userId);

            return updatedSchedule;

        } catch (Exception e) {
            logger.error("スケジュール更新エラー: scheduleId={}, userId={}", scheduleId, userId, e);
            throw new RuntimeException("スケジュールの更新に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * スケジュール削除
     */
    public void deleteSchedule(Long scheduleId, Long userId) {
        try {
            Optional<ReportSchedule> scheduleOpt = scheduleRepository.findById(scheduleId);
            if (scheduleOpt.isEmpty()) {
                throw new IllegalArgumentException("スケジュールが見つかりません: " + scheduleId);
            }

            ReportSchedule schedule = scheduleOpt.get();

            // 権限チェック
            if (!userId.equals(schedule.getUserId())) {
                throw new IllegalArgumentException("スケジュールの削除権限がありません");
            }

            // 論理削除
            schedule.setIsActive(false);
            scheduleRepository.save(schedule);

            logger.info("スケジュール削除完了: scheduleId={}, userId={}", scheduleId, userId);

        } catch (Exception e) {
            logger.error("スケジュール削除エラー: scheduleId={}, userId={}", scheduleId, userId, e);
            throw new RuntimeException("スケジュールの削除に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * ユーザーのスケジュール一覧取得
     */
    public List<ReportSchedule> getUserSchedules(Long userId) {
        try {
            List<ReportSchedule> schedules = scheduleRepository.findByUserIdAndIsActiveTrue(userId);
            logger.info("ユーザースケジュール一覧取得完了: userId={}, count={}", userId, schedules.size());
            return schedules;
        } catch (Exception e) {
            logger.error("ユーザースケジュール一覧取得エラー: userId={}", userId, e);
            throw new RuntimeException("スケジュール一覧の取得に失敗しました", e);
        }
    }

    /**
     * 実行対象スケジュール取得
     */
    public List<ReportSchedule> getSchedulesToRun() {
        try {
            LocalDateTime currentTime = LocalDateTime.now();
            List<ReportSchedule> schedules = scheduleRepository.findSchedulesToRun(currentTime);
            logger.info("実行対象スケジュール取得: count={}, currentTime={}", schedules.size(), currentTime);
            return schedules;
        } catch (Exception e) {
            logger.error("実行対象スケジュール取得エラー", e);
            throw new RuntimeException("実行対象スケジュールの取得に失敗しました", e);
        }
    }

    /**
     * スケジュール実行
     */
    @Async
    public CompletableFuture<Void> executeSchedule(ReportSchedule schedule) {
        try {
            logger.info("スケジュール実行開始: scheduleId={}, name={}", schedule.getId(), schedule.getName());

            // レポートリクエスト構築
            ReportRequest reportRequest = buildReportRequest(schedule);

            // レポート生成実行
            reportService.generateReport(schedule.getUserId(), reportRequest);

            // 実行記録更新
            schedule.setLastRunTime(LocalDateTime.now());
            schedule.setNextRunTime(calculateNextRunTime(
                parseScheduleConfig(schedule.getScheduleConfig()),
                schedule.getScheduleType()
            ));

            scheduleRepository.save(schedule);

            logger.info("スケジュール実行完了: scheduleId={}, nextRunTime={}",
                schedule.getId(), schedule.getNextRunTime());

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            logger.error("スケジュール実行エラー: scheduleId={}", schedule.getId(), e);

            // エラー状態更新
            schedule.setStatus("ERROR");
            schedule.setLastRunTime(LocalDateTime.now());
            scheduleRepository.save(schedule);

            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * スケジュール作成バリデーション
     */
    private void validateScheduleCreation(ScheduleCreateRequest request, Long userId) {
        // 同名チェック
        Optional<ReportSchedule> existing = scheduleRepository.findByUserIdAndName(userId, request.getName());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("同名のスケジュールが既に存在します: " + request.getName());
        }

        // 上限チェック
        long userScheduleCount = scheduleRepository.countActiveSchedulesByUser(userId);
        if (userScheduleCount >= MAX_SCHEDULES_PER_USER) {
            throw new IllegalArgumentException("スケジュール登録上限に達しています（最大" + MAX_SCHEDULES_PER_USER + "件）");
        }

        // スケジュール設定バリデーション
        validateScheduleConfig(request.getScheduleConfig(), request.getScheduleType());
    }

    /**
     * スケジュール設定バリデーション
     */
    private void validateScheduleConfig(Map<String, Object> config, String scheduleType) {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("スケジュール設定が必要です");
        }

        switch (scheduleType.toUpperCase()) {
            case "DAILY":
                validateDailyConfig(config);
                break;
            case "WEEKLY":
                validateWeeklyConfig(config);
                break;
            case "MONTHLY":
                validateMonthlyConfig(config);
                break;
            case "CUSTOM":
                validateCustomConfig(config);
                break;
            default:
                throw new IllegalArgumentException("サポートされていないスケジュールタイプ: " + scheduleType);
        }
    }

    /**
     * 日次スケジュール設定バリデーション
     */
    private void validateDailyConfig(Map<String, Object> config) {
        Integer hour = (Integer) config.get("hour");
        Integer minute = (Integer) config.get("minute");

        if (hour == null || hour < 0 || hour > 23) {
            throw new IllegalArgumentException("時刻（時）は0-23の範囲で指定してください");
        }
        if (minute == null || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("時刻（分）は0-59の範囲で指定してください");
        }
    }

    /**
     * 週次スケジュール設定バリデーション
     */
    private void validateWeeklyConfig(Map<String, Object> config) {
        validateDailyConfig(config);

        Integer dayOfWeek = (Integer) config.get("dayOfWeek");
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("曜日は1-7（月-日）の範囲で指定してください");
        }
    }

    /**
     * 月次スケジュール設定バリデーション
     */
    private void validateMonthlyConfig(Map<String, Object> config) {
        validateDailyConfig(config);

        Integer dayOfMonth = (Integer) config.get("dayOfMonth");
        if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("日付は1-31の範囲で指定してください");
        }
    }

    /**
     * カスタムスケジュール設定バリデーション
     */
    private void validateCustomConfig(Map<String, Object> config) {
        String cronExpression = (String) config.get("cronExpression");
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron式を指定してください");
        }
    }

    /**
     * 次回実行時刻計算
     */
    private LocalDateTime calculateNextRunTime(Map<String, Object> config, String scheduleType) {
        LocalDateTime now = LocalDateTime.now();

        switch (scheduleType.toUpperCase()) {
            case "DAILY":
                return calculateDailyNextRun(config, now);
            case "WEEKLY":
                return calculateWeeklyNextRun(config, now);
            case "MONTHLY":
                return calculateMonthlyNextRun(config, now);
            case "CUSTOM":
                return calculateCustomNextRun(config, now);
            default:
                throw new IllegalArgumentException("サポートされていないスケジュールタイプ: " + scheduleType);
        }
    }

    /**
     * 日次実行時刻計算
     */
    private LocalDateTime calculateDailyNextRun(Map<String, Object> config, LocalDateTime now) {
        Integer hour = (Integer) config.get("hour");
        Integer minute = (Integer) config.get("minute");

        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1);
        }

        return nextRun;
    }

    /**
     * 週次実行時刻計算
     */
    private LocalDateTime calculateWeeklyNextRun(Map<String, Object> config, LocalDateTime now) {
        Integer hour = (Integer) config.get("hour");
        Integer minute = (Integer) config.get("minute");
        Integer dayOfWeek = (Integer) config.get("dayOfWeek");

        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        // 曜日調整（1=月曜日, 7=日曜日）
        int currentDayOfWeek = nextRun.getDayOfWeek().getValue();
        int daysToAdd = (dayOfWeek - currentDayOfWeek + 7) % 7;

        if (daysToAdd == 0 && (nextRun.isBefore(now) || nextRun.isEqual(now))) {
            daysToAdd = 7;
        }

        return nextRun.plusDays(daysToAdd);
    }

    /**
     * 月次実行時刻計算
     */
    private LocalDateTime calculateMonthlyNextRun(Map<String, Object> config, LocalDateTime now) {
        Integer hour = (Integer) config.get("hour");
        Integer minute = (Integer) config.get("minute");
        Integer dayOfMonth = (Integer) config.get("dayOfMonth");

        LocalDateTime nextRun = now.withDayOfMonth(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        // 指定日に調整
        try {
            nextRun = nextRun.withDayOfMonth(dayOfMonth);
        } catch (Exception e) {
            // 月末日超過の場合は月末日に設定
            nextRun = nextRun.withDayOfMonth(nextRun.toLocalDate().lengthOfMonth());
        }

        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusMonths(1);
            try {
                nextRun = nextRun.withDayOfMonth(dayOfMonth);
            } catch (Exception e) {
                nextRun = nextRun.withDayOfMonth(nextRun.toLocalDate().lengthOfMonth());
            }
        }

        return nextRun;
    }

    /**
     * カスタム実行時刻計算
     */
    private LocalDateTime calculateCustomNextRun(Map<String, Object> config, LocalDateTime now) {
        // 簡易実装：1時間後に設定
        return now.plusHours(1);
    }

    /**
     * レポートリクエスト構築
     */
    @SuppressWarnings("unchecked")
    private ReportRequest buildReportRequest(ReportSchedule schedule) throws Exception {
        ReportRequest request = new ReportRequest(schedule.getReportType(), schedule.getFormat());
        request.setUserId(schedule.getUserId());
        request.setTemplateId(schedule.getTemplateId());

        if (schedule.getReportFilters() != null) {
            Map<String, Object> filters = objectMapper.readValue(schedule.getReportFilters(), Map.class);
            request.setFilters(objectMapper.convertValue(filters, ReportRequest.ReportFilters.class));
        }

        if (schedule.getReportOptions() != null) {
            Map<String, Object> options = objectMapper.readValue(schedule.getReportOptions(), Map.class);
            request.setOptions(objectMapper.convertValue(options, ReportRequest.ReportOptions.class));
        }

        return request;
    }

    /**
     * スケジュール設定解析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseScheduleConfig(String scheduleConfigJson) throws Exception {
        return objectMapper.readValue(scheduleConfigJson, Map.class);
    }

    /**
     * スケジュール作成リクエスト
     */
    public static class ScheduleCreateRequest {
        private String name;
        private String reportType;
        private String format;
        private Long templateId;
        private Map<String, Object> reportFilters;
        private Map<String, Object> reportOptions;
        private String scheduleType;
        private Map<String, Object> scheduleConfig;
        private Map<String, Object> outputConfig;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getReportType() { return reportType; }
        public void setReportType(String reportType) { this.reportType = reportType; }

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }

        public Map<String, Object> getReportFilters() { return reportFilters; }
        public void setReportFilters(Map<String, Object> reportFilters) { this.reportFilters = reportFilters; }

        public Map<String, Object> getReportOptions() { return reportOptions; }
        public void setReportOptions(Map<String, Object> reportOptions) { this.reportOptions = reportOptions; }

        public String getScheduleType() { return scheduleType; }
        public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }

        public Map<String, Object> getScheduleConfig() { return scheduleConfig; }
        public void setScheduleConfig(Map<String, Object> scheduleConfig) { this.scheduleConfig = scheduleConfig; }

        public Map<String, Object> getOutputConfig() { return outputConfig; }
        public void setOutputConfig(Map<String, Object> outputConfig) { this.outputConfig = outputConfig; }
    }

    /**
     * スケジュール更新リクエスト
     */
    public static class ScheduleUpdateRequest {
        private String name;
        private Map<String, Object> scheduleConfig;
        private Map<String, Object> reportFilters;
        private Map<String, Object> outputConfig;
        private String status;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Map<String, Object> getScheduleConfig() { return scheduleConfig; }
        public void setScheduleConfig(Map<String, Object> scheduleConfig) { this.scheduleConfig = scheduleConfig; }

        public Map<String, Object> getReportFilters() { return reportFilters; }
        public void setReportFilters(Map<String, Object> reportFilters) { this.reportFilters = reportFilters; }

        public Map<String, Object> getOutputConfig() { return outputConfig; }
        public void setOutputConfig(Map<String, Object> outputConfig) { this.outputConfig = outputConfig; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * 未実行スケジュール取得
     */
    public List<ReportSchedule> findNeverRunSchedules() {
        try {
            return scheduleRepository.findNeverRunSchedules();
        } catch (Exception e) {
            logger.error("未実行スケジュール取得エラー", e);
            return List.of();
        }
    }

    /**
     * ステータス別スケジュール取得
     */
    public List<ReportSchedule> findByStatusAndIsActiveTrue(String status) {
        try {
            return scheduleRepository.findByStatusAndIsActiveTrue(status);
        } catch (Exception e) {
            logger.error("ステータス別スケジュール取得エラー: status={}", status, e);
            return List.of();
        }
    }

    /**
     * 全スケジュール数取得
     */
    public long count() {
        try {
            return scheduleRepository.count();
        } catch (Exception e) {
            logger.error("スケジュール数取得エラー", e);
            return 0;
        }
    }

    /**
     * スケジュールタイプ別アクティブスケジュール取得
     */
    public List<ReportSchedule> findByScheduleTypeAndActive(String scheduleType) {
        try {
            return scheduleRepository.findByScheduleTypeAndActive(scheduleType);
        } catch (Exception e) {
            logger.error("スケジュールタイプ別取得エラー: scheduleType={}", scheduleType, e);
            return List.of();
        }
    }
}