package com.library.management.controller;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.ReportHistory;
import com.library.management.service.report.ExcelReportService;
import com.library.management.service.report.PDFReportService;
import com.library.management.service.report.ReportFileService;
import com.library.management.service.report.ReportService;
import com.library.management.service.report.data.ReportDataService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 帳票生成コントローラー
 * 帳票の生成、ダウンロード、履歴管理のエンドポイントを提供
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = {"http://localhost:3000"})
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private PDFReportService pdfReportService;

    @Autowired
    private ExcelReportService excelReportService;

    @Autowired
    private ReportFileService reportFileService;

    @Autowired
    private ReportDataService reportDataService;

    /**
     * 帳票生成（非同期対応）
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateReport(
            @Valid @RequestBody ReportRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // ユーザーID取得
            Long userId = getUserId(authentication);

            // 帳票サービス選択
            ReportService reportService = getReportService(request.getFormat());

            // 非同期生成フラグ判定
            boolean isAsyncRequired = shouldUseAsyncGeneration(request);

            if (isAsyncRequired) {
                // 非同期帳票生成
                ReportService.ReportGenerationResult result = reportService.generateReportAsync(userId, request);
                response.put("success", true);
                response.put("async", true);
                response.put("message", "帳票生成を開始しました。進捗は履歴から確認できます。");
                response.put("reportId", result.getReportId());
                response.put("statusUrl", "/api/reports/status/" + result.getReportId());

                logger.info("非同期帳票生成開始: userId={}, reportType={}, format={}, reportId={}",
                    userId, request.getReportType(), request.getFormat(), result.getReportId());

                return ResponseEntity.ok(response);
            } else {
                // 同期帳票生成
                ReportService.ReportGenerationResult result = reportService.generateReport(userId, request);

                if (result.isSuccess()) {
                    response.put("success", true);
                    response.put("async", false);
                    response.put("message", result.getMessage());
                    response.put("reportId", result.getReportId());
                    response.put("downloadUrl", "/api/reports/download/" + result.getReportId());

                    // ファイル情報追加
                    ReportFileService.FileInfo fileInfo = reportFileService.getFileInfo(result.getFilePath());
                    if (fileInfo != null) {
                        response.put("fileInfo", Map.of(
                            "fileName", fileInfo.getFileName(),
                            "fileSize", fileInfo.getFileSize(),
                            "contentType", fileInfo.getContentType()
                        ));
                    }

                    logger.info("同期帳票生成成功: userId={}, reportType={}, format={}, reportId={}",
                        userId, request.getReportType(), request.getFormat(), result.getReportId());

                    return ResponseEntity.ok(response);
                } else {
                    response.put("success", false);
                    response.put("message", result.getMessage());
                    return ResponseEntity.badRequest().body(response);
                }
            }

        } catch (IllegalArgumentException e) {
            logger.warn("帳票生成リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (IllegalStateException e) {
            logger.warn("帳票生成認証エラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "認証エラー: " + e.getMessage());
            response.put("errorType", "AUTHENTICATION_ERROR");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);

        } catch (ClassCastException e) {
            logger.error("帳票生成型変換エラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "認証情報の型エラーが発生しました: " + e.getMessage());
            response.put("errorType", "TYPE_CONVERSION_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (RuntimeException e) {
            logger.error("帳票生成ランタイムエラー", e);
            response.put("success", false);
            response.put("message", "帳票生成処理でエラーが発生しました: " + e.getMessage());
            response.put("errorType", "RUNTIME_ERROR");
            response.put("errorClass", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (Exception e) {
            logger.error("帳票生成予期しないエラー", e);
            response.put("success", false);
            response.put("message", "予期しないエラーが発生しました: " + e.getMessage());
            response.put("errorType", "UNEXPECTED_ERROR");
            response.put("errorClass", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 帳票ダウンロード
     */
    @GetMapping("/download/{reportId}")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable Long reportId,
            Authentication authentication) {

        try {
            // ユーザーID取得
            Long userId = getUserId(authentication);

            // ファイル取得
            Resource resource = reportFileService.getReportFile(userId, reportId);

            // ファイル情報取得
            ReportFileService.FileInfo fileInfo = reportFileService.getFileInfo(resource.getFile().getAbsolutePath());

            // レスポンスヘッダー設定
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileInfo.getFileName() + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, fileInfo.getContentType());

            logger.info("帳票ダウンロード: userId={}, reportId={}, fileName={}",
                userId, reportId, fileInfo.getFileName());

            return ResponseEntity.ok()
                .headers(headers)
                .body(resource);

        } catch (IllegalArgumentException e) {
            logger.warn("帳票ダウンロードエラー: userId={}, reportId={}, error={}",
                getUserId(authentication), reportId, e.getMessage());
            return ResponseEntity.notFound().build();

        } catch (IllegalStateException e) {
            logger.warn("帳票状態エラー: userId={}, reportId={}, error={}",
                getUserId(authentication), reportId, e.getMessage());
            return ResponseEntity.status(HttpStatus.GONE).build();

        } catch (IOException e) {
            logger.error("帳票ファイル読み取りエラー: userId={}, reportId={}",
                getUserId(authentication), reportId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } catch (Exception e) {
            logger.error("帳票ダウンロード予期しないエラー: userId={}, reportId={}",
                getUserId(authentication), reportId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 帳票履歴取得
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getReportHistory(
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // ユーザーID取得
            Long userId = getUserId(authentication);

            // 履歴取得
            List<ReportHistory> history = pdfReportService.getUserReportHistory(userId, limit);

            response.put("success", true);
            response.put("reports", history.stream()
                .map(this::convertToReportDto)
                .toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("帳票履歴取得エラー", e);
            response.put("success", false);
            response.put("message", "履歴の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 帳票生成状況取得
     */
    @GetMapping("/status/{reportId}")
    public ResponseEntity<Map<String, Object>> getReportStatus(
            @PathVariable Long reportId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // ユーザーID取得
            Long userId = getUserId(authentication);

            // レポート状況取得
            Optional<ReportHistory> reportOpt = pdfReportService.getReportById(reportId, userId);
            if (reportOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "指定されたレポートが見つかりません");
                return ResponseEntity.notFound().build();
            }

            ReportHistory report = reportOpt.get();
            response.put("success", true);
            response.put("status", report.getStatus());
            response.put("progress", calculateProgress(report));

            if ("COMPLETED".equals(report.getStatus())) {
                response.put("downloadUrl", "/api/reports/download/" + reportId);

                // ファイル情報追加
                if (report.getFilePath() != null) {
                    ReportFileService.FileInfo fileInfo = reportFileService.getFileInfo(report.getFilePath());
                    if (fileInfo != null) {
                        response.put("fileInfo", Map.of(
                            "fileName", fileInfo.getFileName(),
                            "fileSize", fileInfo.getFileSize(),
                            "contentType", fileInfo.getContentType()
                        ));
                    }
                }
            } else if ("FAILED".equals(report.getStatus())) {
                response.put("message", "帳票生成に失敗しました");
            } else {
                response.put("message", "帳票を生成中です...");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("帳票状況取得エラー", e);
            response.put("success", false);
            response.put("message", "状況の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 書籍統計取得
     */
    @PostMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getBookStatistics(
            @RequestBody(required = false) ReportRequest.ReportFilters filters,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // ユーザーID取得
            Long userId = getUserId(authentication);

            // 統計データ取得
            ReportDataService.BookStatistics statistics = reportDataService.getBookStatistics(userId, filters);

            response.put("success", true);
            response.put("statistics", Map.of(
                "totalCount", statistics.getTotalCount(),
                "statusCounts", statistics.getStatusCounts(),
                "publisherCounts", statistics.getPublisherCounts()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("書籍統計取得エラー", e);
            response.put("success", false);
            response.put("message", "統計データの取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 帳票プレビュー生成（実際のファイル生成）
     */
    @PostMapping("/generate-preview")
    public ResponseEntity<Map<String, Object>> generateReportPreview(
            @Valid @RequestBody ReportRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            // ユーザーID取得
            Long userId = getUserId(authentication);

            // 帳票サービス選択
            ReportService reportService = getReportService(request.getFormat());

            // 実際の帳票生成（同期処理で実行）
            ReportService.ReportGenerationResult result = reportService.generateReport(userId, request);

            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", "プレビューを生成しました");
                response.put("reportId", result.getReportId());
                response.put("previewUrl", "/api/reports/preview-content/" + result.getReportId());
                response.put("downloadUrl", "/api/reports/download/" + result.getReportId());

                // キャッシュヒット情報追加
                response.put("cacheHit", result.isCacheHit());
                if (result.getGenerationTimeMs() != null) {
                    response.put("generationTimeMs", result.getGenerationTimeMs());
                }

                // ファイル情報追加
                ReportFileService.FileInfo fileInfo = reportFileService.getFileInfo(result.getFilePath());
                if (fileInfo != null) {
                    response.put("fileInfo", Map.of(
                        "fileName", fileInfo.getFileName(),
                        "fileSize", fileInfo.getFileSize(),
                        "contentType", fileInfo.getContentType()
                    ));
                }

                logger.info("プレビュー生成成功: userId={}, reportType={}, format={}, reportId={}",
                    userId, request.getReportType(), request.getFormat(), result.getReportId());

                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", result.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("プレビュー生成エラー", e);
            response.put("success", false);
            response.put("message", "プレビューの生成に失敗しました: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * プレビュー内容取得
     */
    @GetMapping("/preview-content/{reportId}")
    public ResponseEntity<Resource> getPreviewContent(
            @PathVariable Long reportId,
            Authentication authentication) {

        try {
            // ユーザーID取得
            Long userId = getUserId(authentication);

            // ファイル取得（実際に生成されたファイルを取得）
            Resource resource = reportFileService.getReportFile(userId, reportId);

            // ファイル情報取得
            ReportFileService.FileInfo fileInfo = reportFileService.getFileInfo(resource.getFile().getAbsolutePath());

            // レスポンスヘッダー設定（inline表示）
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileInfo.getFileName() + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, fileInfo.getContentType());
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            logger.info("プレビュー表示: userId={}, reportId={}, fileName={}, fileSize={}",
                userId, reportId, fileInfo.getFileName(), fileInfo.getFileSize());

            return ResponseEntity.ok()
                .headers(headers)
                .contentLength(fileInfo.getFileSize())
                .body(resource);

        } catch (Exception e) {
            logger.error("プレビュー表示エラー: userId={}, reportId={}",
                getUserId(authentication), reportId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 帳票サービス選択
     */
    private ReportService getReportService(String format) {
        switch (format.toUpperCase()) {
            case "PDF":
                return pdfReportService;
            case "EXCEL":
                return excelReportService;
            default:
                throw new IllegalArgumentException("サポートされていないフォーマット: " + format);
        }
    }

    /**
     * ユーザーID取得
     */
    private Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("認証情報が見つかりません");
        }

        Object principal = authentication.getPrincipal();

        // UserPrincipalから直接IDを取得
        if (principal instanceof com.library.management.security.UserPrincipal) {
            return ((com.library.management.security.UserPrincipal) principal).getId();
        }

        // 後方互換性のためUserエンティティもサポート
        if (principal instanceof com.library.management.entity.User) {
            return ((com.library.management.entity.User) principal).getId();
        }

        throw new IllegalStateException("有効なユーザー情報が見つかりません。Principal type: " + principal.getClass().getName());
    }

    /**
     * ReportHistory を DTO に変換
     */
    private Map<String, Object> convertToReportDto(ReportHistory report) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", report.getId());
        dto.put("reportType", report.getReportType());
        dto.put("format", report.getFormat());
        dto.put("status", report.getStatus());
        dto.put("createdAt", report.getCreatedAt());
        dto.put("expiresAt", report.getExpiresAt());

        if (report.getFileSize() != null) {
            dto.put("fileSize", report.getFileSize());
        }

        if ("COMPLETED".equals(report.getStatus())) {
            dto.put("downloadUrl", "/api/reports/download/" + report.getId());
        }

        return dto;
    }

    /**
     * 非同期生成が必要かどうか判定
     */
    private boolean shouldUseAsyncGeneration(ReportRequest request) {
        // Excel形式は常に非同期
        if ("EXCEL".equalsIgnoreCase(request.getFormat())) {
            return true;
        }

        // system レポートは非同期
        if ("system".equalsIgnoreCase(request.getReportType())) {
            return true;
        }

        // 複雑なフィルターが設定されている場合は非同期
        if (request.getFilters() != null) {
            ReportRequest.ReportFilters filters = request.getFilters();
            if ((filters.getStartDate() != null && filters.getEndDate() != null) ||
                (filters.getAuthor() != null && !filters.getAuthor().trim().isEmpty()) ||
                (filters.getGenre() != null && !filters.getGenre().trim().isEmpty()) ||
                (filters.getPublisher() != null && !filters.getPublisher().trim().isEmpty())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 進捗率計算
     */
    private int calculateProgress(ReportHistory report) {
        switch (report.getStatus()) {
            case "GENERATING":
                // 実際の進捗は時間ベースで推定
                long elapsed = java.time.Duration.between(report.getCreatedAt(), java.time.LocalDateTime.now()).toSeconds();
                if (elapsed < 10) return 25;
                if (elapsed < 30) return 50;
                if (elapsed < 60) return 75;
                return 90;
            case "COMPLETED":
                return 100;
            case "FAILED":
                return 0;
            default:
                return 0;
        }
    }

    /**
     * プレビューデータ生成
     */
    private Map<String, Object> generatePreviewData(Long userId, ReportRequest request) {
        Map<String, Object> previewData = new HashMap<>();

        try {
            // 統計データ取得
            ReportDataService.BookStatistics statistics = reportDataService.getBookStatistics(userId, request.getFilters());

            // 基本情報
            previewData.put("reportType", request.getReportType());
            previewData.put("format", request.getFormat());
            previewData.put("generatedAt", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")));

            // 統計情報
            previewData.put("totalBooks", statistics.getTotalCount());
            previewData.put("statusCounts", statistics.getStatusCounts());

            // サンプルデータ（最初の5件程度）
            if ("personal".equals(request.getReportType()) || "system".equals(request.getReportType())) {
                List<Map<String, Object>> sampleBooks = generateSampleBookData(userId, request, 5);
                previewData.put("sampleBooks", sampleBooks);
            }

            // フィルター情報
            if (request.getFilters() != null) {
                Map<String, Object> filterInfo = new HashMap<>();
                if (request.getFilters().getReadStatus() != null && !request.getFilters().getReadStatus().isEmpty()) {
                    filterInfo.put("readStatus", request.getFilters().getReadStatus());
                }
                if (request.getFilters().getStartDate() != null) {
                    filterInfo.put("startDate", request.getFilters().getStartDate());
                }
                if (request.getFilters().getEndDate() != null) {
                    filterInfo.put("endDate", request.getFilters().getEndDate());
                }
                if (request.getFilters().getPublisher() != null && !request.getFilters().getPublisher().trim().isEmpty()) {
                    filterInfo.put("publisher", request.getFilters().getPublisher());
                }
                if (request.getFilters().getAuthor() != null && !request.getFilters().getAuthor().trim().isEmpty()) {
                    filterInfo.put("author", request.getFilters().getAuthor());
                }
                if (request.getFilters().getGenre() != null && !request.getFilters().getGenre().trim().isEmpty()) {
                    filterInfo.put("genre", request.getFilters().getGenre());
                }
                previewData.put("appliedFilters", filterInfo);
            }

            // 予想ファイルサイズ（推定）
            int estimatedPages = Math.max(1, statistics.getTotalCount() / 20);
            String estimatedSize;
            if ("PDF".equalsIgnoreCase(request.getFormat())) {
                estimatedSize = String.format("約 %d ページ (%d KB)", estimatedPages, estimatedPages * 50);
            } else {
                estimatedSize = String.format("約 %d KB", statistics.getTotalCount() * 2);
            }
            previewData.put("estimatedSize", estimatedSize);

        } catch (Exception e) {
            logger.warn("プレビューデータ生成中にエラーが発生しましたが続行します", e);
            previewData.put("error", "一部のプレビューデータを生成できませんでした");
        }

        return previewData;
    }

    /**
     * サンプル書籍データ生成
     */
    private List<Map<String, Object>> generateSampleBookData(Long userId, ReportRequest request, int limit) {
        try {
            // 実際の書籍データを少数取得してサンプルとして使用
            ReportDataService.BookStatistics statistics = reportDataService.getBookStatistics(userId, request.getFilters());

            // 簡易的なサンプルデータ生成（実装に応じて調整）
            List<Map<String, Object>> sampleBooks = new java.util.ArrayList<>();

            for (int i = 0; i < Math.min(limit, 3); i++) {
                Map<String, Object> book = new HashMap<>();
                book.put("id", i + 1);
                book.put("title", "サンプル書籍 " + (i + 1));
                book.put("author", "サンプル著者 " + (i + 1));
                book.put("publisher", "サンプル出版社");
                book.put("status", i == 0 ? "読了" : i == 1 ? "読書中" : "未読");
                book.put("registeredAt", java.time.LocalDate.now().minusDays(i * 10));
                sampleBooks.add(book);
            }

            return sampleBooks;

        } catch (Exception e) {
            logger.warn("サンプル書籍データ生成エラー", e);
            return new java.util.ArrayList<>();
        }
    }
}