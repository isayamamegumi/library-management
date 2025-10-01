package com.library.management.controller;

import com.library.management.entity.ReportSchedule;
import com.library.management.service.report.schedule.ReportScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * レポートスケジュールコントローラー
 */
@RestController
@RequestMapping("/api/schedules")
public class ReportScheduleController {

    private static final Logger logger = LoggerFactory.getLogger(ReportScheduleController.class);

    @Autowired
    private ReportScheduleService scheduleService;

    /**
     * スケジュール一覧取得
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSchedules(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            List<ReportSchedule> schedules = scheduleService.getUserSchedules(userId);

            response.put("success", true);
            response.put("schedules", schedules.stream().map(this::convertToDto).toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("スケジュール一覧取得エラー", e);
            response.put("success", false);
            response.put("message", "スケジュール一覧の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * スケジュール作成
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSchedule(
            @Valid @RequestBody ReportScheduleService.ScheduleCreateRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            ReportSchedule schedule = scheduleService.createSchedule(request, userId);

            response.put("success", true);
            response.put("message", "スケジュールを作成しました");
            response.put("schedule", convertToDto(schedule));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("スケジュール作成リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("スケジュール作成エラー", e);
            response.put("success", false);
            response.put("message", "スケジュールの作成に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * スケジュール更新
     */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> updateSchedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody ReportScheduleService.ScheduleUpdateRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            ReportSchedule schedule = scheduleService.updateSchedule(scheduleId, request, userId);

            response.put("success", true);
            response.put("message", "スケジュールを更新しました");
            response.put("schedule", convertToDto(schedule));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("スケジュール更新リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("スケジュール更新エラー", e);
            response.put("success", false);
            response.put("message", "スケジュールの更新に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * スケジュール削除
     */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> deleteSchedule(
            @PathVariable Long scheduleId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            scheduleService.deleteSchedule(scheduleId, userId);

            response.put("success", true);
            response.put("message", "スケジュールを削除しました");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("スケジュール削除リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("スケジュール削除エラー", e);
            response.put("success", false);
            response.put("message", "スケジュールの削除に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * スケジュール詳細取得
     */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> getSchedule(
            @PathVariable Long scheduleId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            List<ReportSchedule> userSchedules = scheduleService.getUserSchedules(userId);

            ReportSchedule schedule = userSchedules.stream()
                .filter(s -> s.getId().equals(scheduleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("スケジュールが見つかりません"));

            response.put("success", true);
            response.put("schedule", convertToDto(schedule));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("スケジュール詳細取得リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("スケジュール詳細取得エラー", e);
            response.put("success", false);
            response.put("message", "スケジュール詳細の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * スケジュール実行（手動）
     */
    @PostMapping("/{scheduleId}/execute")
    public ResponseEntity<Map<String, Object>> executeSchedule(
            @PathVariable Long scheduleId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            List<ReportSchedule> userSchedules = scheduleService.getUserSchedules(userId);

            ReportSchedule schedule = userSchedules.stream()
                .filter(s -> s.getId().equals(scheduleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("スケジュールが見つかりません"));

            // 非同期実行
            scheduleService.executeSchedule(schedule);

            response.put("success", true);
            response.put("message", "スケジュールの実行を開始しました");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("スケジュール実行リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("スケジュール実行エラー", e);
            response.put("success", false);
            response.put("message", "スケジュールの実行に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * ユーザーID取得
     */
    private Long getUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    /**
     * スケジュールDTO変換
     */
    private Map<String, Object> convertToDto(ReportSchedule schedule) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", schedule.getId());
        dto.put("name", schedule.getName());
        dto.put("reportType", schedule.getReportType());
        dto.put("format", schedule.getFormat());
        dto.put("templateId", schedule.getTemplateId());
        dto.put("scheduleType", schedule.getScheduleType());
        dto.put("nextRunTime", schedule.getNextRunTime());
        dto.put("lastRunTime", schedule.getLastRunTime());
        dto.put("status", schedule.getStatus());
        dto.put("isActive", schedule.getIsActive());
        dto.put("createdAt", schedule.getCreatedAt());
        dto.put("updatedAt", schedule.getUpdatedAt());

        // 設定データはJSONから解析して返す
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (schedule.getScheduleConfig() != null) {
                dto.put("scheduleConfig", mapper.readValue(schedule.getScheduleConfig(), Map.class));
            }
            if (schedule.getReportFilters() != null) {
                dto.put("reportFilters", mapper.readValue(schedule.getReportFilters(), Map.class));
            }
            if (schedule.getOutputConfig() != null) {
                dto.put("outputConfig", mapper.readValue(schedule.getOutputConfig(), Map.class));
            }
        } catch (Exception e) {
            logger.warn("スケジュール設定解析エラー: scheduleId={}", schedule.getId(), e);
        }

        return dto;
    }
}