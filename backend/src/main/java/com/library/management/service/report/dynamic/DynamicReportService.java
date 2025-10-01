package com.library.management.service.report.dynamic;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.ReportTemplate;
import com.library.management.service.report.template.TemplateService;
import com.library.management.service.report.data.ReportDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 動的レポート生成サービス
 * テンプレートとデータを組み合わせて柔軟なレポートを生成
 */
@Service
public class DynamicReportService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicReportService.class);

    @Autowired
    private TemplateService templateService;

    @Autowired
    private ReportDataService reportDataService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 動的レポート生成
     */
    public DynamicReportResult generateDynamicReport(Long userId, ReportRequest request) {
        try {
            logger.info("動的レポート生成開始: userId={}, reportType={}, templateId={}",
                userId, request.getReportType(), request.getTemplateId());

            // 1. テンプレート取得
            ReportTemplate template = getReportTemplate(request, userId);

            // 2. テンプレート解析
            Map<String, Object> templateData = templateService.parseTemplateData(template.getTemplateData());

            // 3. データ取得・加工
            Map<String, Object> reportData = prepareReportData(userId, request, templateData);

            // 4. 動的構成生成
            DynamicReportConfig config = buildDynamicConfig(templateData, reportData, request);

            // 5. レポート構造生成
            DynamicReportStructure structure = buildReportStructure(config, reportData);

            DynamicReportResult result = new DynamicReportResult(template, config, structure, reportData);

            logger.info("動的レポート生成成功: userId={}, templateId={}, dataCount={}",
                userId, template.getId(), reportData.size());

            return result;

        } catch (Exception e) {
            logger.error("動的レポート生成エラー: userId={}, reportType={}",
                userId, request.getReportType(), e);
            throw new RuntimeException("動的レポートの生成に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * テンプレート取得
     */
    private ReportTemplate getReportTemplate(ReportRequest request, Long userId) {
        if (request.getTemplateId() != null) {
            // 指定テンプレート使用
            List<ReportTemplate> templates = templateService.getAccessibleTemplates(
                userId, request.getFormat(), request.getReportType());

            return templates.stream()
                .filter(t -> t.getId().equals(request.getTemplateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("指定されたテンプレートにアクセスできません"));
        } else {
            // デフォルトテンプレート使用
            return templateService.getDefaultTemplate(request.getFormat(), request.getReportType())
                .orElseThrow(() -> new IllegalArgumentException("デフォルトテンプレートが見つかりません"));
        }
    }

    /**
     * レポートデータ準備
     */
    private Map<String, Object> prepareReportData(Long userId, ReportRequest request,
                                                 Map<String, Object> templateData) {
        Map<String, Object> reportData = new HashMap<>();

        try {
            // 基本データ取得
            switch (request.getReportType().toUpperCase()) {
                case "PERSONAL":
                case "BOOK_LIST":
                    // 書籍データ取得
                    reportData.put("books", reportDataService.getFilteredBooks(userId, request));
                    reportData.put("statistics", reportDataService.getBookStatistics(userId, request.getFilters()));
                    break;

                case "SYSTEM":
                case "READING_STATS":
                    // 管理者用全体統計
                    reportData.put("allBooks", reportDataService.getAllBooks(request));
                    reportData.put("systemStatistics", reportDataService.getSystemStatistics(request.getFilters()));
                    reportData.put("userStatistics", reportDataService.getUserStatistics(request.getFilters()));
                    break;

                default:
                    throw new IllegalArgumentException("サポートされていないレポートタイプ: " + request.getReportType());
            }

            // 動的フィールド追加
            addDynamicFields(reportData, request, templateData);

            // 計算フィールド追加
            addCalculatedFields(reportData, templateData);

            logger.debug("レポートデータ準備完了: keys={}", reportData.keySet());

        } catch (Exception e) {
            logger.error("レポートデータ準備エラー", e);
            throw new RuntimeException("レポートデータの準備に失敗しました", e);
        }

        return reportData;
    }

    /**
     * 動的フィールド追加
     */
    @SuppressWarnings("unchecked")
    private void addDynamicFields(Map<String, Object> reportData, ReportRequest request,
                                 Map<String, Object> templateData) {
        // テンプレートで定義された動的フィールドを追加
        Map<String, Object> dynamicFields = (Map<String, Object>) templateData.get("dynamicFields");
        if (dynamicFields != null) {
            for (Map.Entry<String, Object> entry : dynamicFields.entrySet()) {
                String fieldName = entry.getKey();
                Map<String, Object> fieldConfig = (Map<String, Object>) entry.getValue();

                Object fieldValue = calculateDynamicField(fieldName, fieldConfig, reportData, request);
                reportData.put(fieldName, fieldValue);
            }
        }
    }

    /**
     * 計算フィールド追加
     */
    @SuppressWarnings("unchecked")
    private void addCalculatedFields(Map<String, Object> reportData, Map<String, Object> templateData) {
        Map<String, Object> calculations = (Map<String, Object>) templateData.get("calculations");
        if (calculations != null) {
            for (Map.Entry<String, Object> entry : calculations.entrySet()) {
                String calcName = entry.getKey();
                Map<String, Object> calcConfig = (Map<String, Object>) entry.getValue();

                Object calcValue = performCalculation(calcConfig, reportData);
                reportData.put(calcName, calcValue);
            }
        }
    }

    /**
     * 動的フィールド計算
     */
    @SuppressWarnings("unchecked")
    private Object calculateDynamicField(String fieldName, Map<String, Object> fieldConfig,
                                        Map<String, Object> reportData, ReportRequest request) {
        String type = (String) fieldConfig.get("type");
        String source = (String) fieldConfig.get("source");

        switch (type) {
            case "filter_summary":
                return generateFilterSummary(request.getFilters());

            case "date_range":
                return generateDateRange(request.getFilters());

            case "user_info":
                return Map.of(
                    "userId", request.getUserId(),
                    "generatedAt", new Date(),
                    "reportType", request.getReportType()
                );

            case "custom_calculation":
                return performCustomCalculation((Map<String, Object>) fieldConfig.get("calculation"), reportData);

            default:
                logger.warn("未知の動的フィールドタイプ: {}", type);
                return null;
        }
    }

    /**
     * 計算実行
     */
    @SuppressWarnings("unchecked")
    private Object performCalculation(Map<String, Object> calcConfig, Map<String, Object> reportData) {
        String operation = (String) calcConfig.get("operation");
        String sourceField = (String) calcConfig.get("sourceField");

        Object sourceData = reportData.get(sourceField);
        if (!(sourceData instanceof List)) {
            return null;
        }

        List<Map<String, Object>> dataList = (List<Map<String, Object>>) sourceData;

        switch (operation) {
            case "count":
                return dataList.size();

            case "sum":
                String sumField = (String) calcConfig.get("field");
                return dataList.stream()
                    .mapToDouble(item -> {
                        Object value = item.get(sumField);
                        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
                    })
                    .sum();

            case "average":
                String avgField = (String) calcConfig.get("field");
                return dataList.stream()
                    .mapToDouble(item -> {
                        Object value = item.get(avgField);
                        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
                    })
                    .average()
                    .orElse(0.0);

            case "group_by":
                String groupField = (String) calcConfig.get("field");
                return dataList.stream()
                    .collect(Collectors.groupingBy(
                        item -> String.valueOf(item.get(groupField)),
                        Collectors.counting()));

            default:
                logger.warn("未知の計算操作: {}", operation);
                return null;
        }
    }

    /**
     * カスタム計算実行
     */
    private Object performCustomCalculation(Map<String, Object> calculation, Map<String, Object> reportData) {
        // 複雑なカスタム計算ロジック
        // 将来的にスクリプトエンジンやルールエンジンの使用も検討
        return "カスタム計算結果";
    }

    /**
     * フィルター概要生成
     */
    private String generateFilterSummary(ReportRequest.ReportFilters filters) {
        if (filters == null) {
            return "フィルターなし";
        }

        List<String> summary = new ArrayList<>();

        if (filters.getReadStatus() != null && !filters.getReadStatus().isEmpty()) {
            summary.add("読書状況: " + String.join(", ", filters.getReadStatus()));
        }

        if (filters.getStartDate() != null && filters.getEndDate() != null) {
            summary.add("期間: " + filters.getStartDate() + " - " + filters.getEndDate());
        }

        if (filters.getPublisher() != null && !filters.getPublisher().trim().isEmpty()) {
            summary.add("出版社: " + filters.getPublisher());
        }

        if (filters.getAuthor() != null && !filters.getAuthor().trim().isEmpty()) {
            summary.add("著者: " + filters.getAuthor());
        }

        return summary.isEmpty() ? "フィルターなし" : String.join(" | ", summary);
    }

    /**
     * 日付範囲生成
     */
    private Map<String, Object> generateDateRange(ReportRequest.ReportFilters filters) {
        Map<String, Object> dateRange = new HashMap<>();

        if (filters != null) {
            dateRange.put("startDate", filters.getStartDate());
            dateRange.put("endDate", filters.getEndDate());
        }

        if (!dateRange.containsKey("startDate")) {
            dateRange.put("startDate", "制限なし");
        }
        if (!dateRange.containsKey("endDate")) {
            dateRange.put("endDate", "制限なし");
        }

        return dateRange;
    }

    /**
     * 動的設定構築
     */
    private DynamicReportConfig buildDynamicConfig(Map<String, Object> templateData,
                                                  Map<String, Object> reportData,
                                                  ReportRequest request) {
        return new DynamicReportConfig(templateData, request);
    }

    /**
     * レポート構造構築
     */
    @SuppressWarnings("unchecked")
    private DynamicReportStructure buildReportStructure(DynamicReportConfig config,
                                                       Map<String, Object> reportData) {
        DynamicReportStructure structure = new DynamicReportStructure();

        // ヘッダー設定
        Map<String, Object> headerConfig = (Map<String, Object>) config.getTemplateData().get("header");
        if (headerConfig != null) {
            structure.setHeader(buildHeaderStructure(headerConfig, reportData));
        }

        // コンテンツ設定
        Map<String, Object> contentConfig = (Map<String, Object>) config.getTemplateData().get("content");
        if (contentConfig != null) {
            structure.setContent(buildContentStructure(contentConfig, reportData));
        }

        // フッター設定
        Map<String, Object> footerConfig = (Map<String, Object>) config.getTemplateData().get("footer");
        if (footerConfig != null) {
            structure.setFooter(buildFooterStructure(footerConfig, reportData));
        }

        return structure;
    }

    /**
     * ヘッダー構造構築
     */
    private Map<String, Object> buildHeaderStructure(Map<String, Object> headerConfig,
                                                    Map<String, Object> reportData) {
        Map<String, Object> header = new HashMap<>(headerConfig);

        // 動的タイトル生成
        if (header.get("dynamicTitle") != null) {
            String title = generateDynamicTitle((String) header.get("dynamicTitle"), reportData);
            header.put("title", title);
        }

        return header;
    }

    /**
     * コンテンツ構造構築
     */
    private Map<String, Object> buildContentStructure(Map<String, Object> contentConfig,
                                                     Map<String, Object> reportData) {
        return new HashMap<>(contentConfig);
    }

    /**
     * フッター構造構築
     */
    private Map<String, Object> buildFooterStructure(Map<String, Object> footerConfig,
                                                    Map<String, Object> reportData) {
        return new HashMap<>(footerConfig);
    }

    /**
     * 動的タイトル生成
     */
    private String generateDynamicTitle(String template, Map<String, Object> reportData) {
        // テンプレート文字列の置換処理
        String title = template;

        // 基本的なプレースホルダー置換
        title = title.replace("${date}", new Date().toString());
        title = title.replace("${count}", String.valueOf(getDataCount(reportData)));

        return title;
    }

    /**
     * データ件数取得
     */
    @SuppressWarnings("unchecked")
    private int getDataCount(Map<String, Object> reportData) {
        Object books = reportData.get("books");
        if (books instanceof List) {
            return ((List<?>) books).size();
        }
        return 0;
    }

    /**
     * 動的レポート結果クラス
     */
    public static class DynamicReportResult {
        private final ReportTemplate template;
        private final DynamicReportConfig config;
        private final DynamicReportStructure structure;
        private final Map<String, Object> data;

        public DynamicReportResult(ReportTemplate template, DynamicReportConfig config,
                                  DynamicReportStructure structure, Map<String, Object> data) {
            this.template = template;
            this.config = config;
            this.structure = structure;
            this.data = data;
        }

        // Getters
        public ReportTemplate getTemplate() { return template; }
        public DynamicReportConfig getConfig() { return config; }
        public DynamicReportStructure getStructure() { return structure; }
        public Map<String, Object> getData() { return data; }
    }

    /**
     * 動的レポート設定クラス
     */
    public static class DynamicReportConfig {
        private final Map<String, Object> templateData;
        private final ReportRequest request;

        public DynamicReportConfig(Map<String, Object> templateData, ReportRequest request) {
            this.templateData = templateData;
            this.request = request;
        }

        public Map<String, Object> getTemplateData() { return templateData; }
        public ReportRequest getRequest() { return request; }
    }

    /**
     * 動的レポート構造クラス
     */
    public static class DynamicReportStructure {
        private Map<String, Object> header;
        private Map<String, Object> content;
        private Map<String, Object> footer;

        // Getters and Setters
        public Map<String, Object> getHeader() { return header; }
        public void setHeader(Map<String, Object> header) { this.header = header; }

        public Map<String, Object> getContent() { return content; }
        public void setContent(Map<String, Object> content) { this.content = content; }

        public Map<String, Object> getFooter() { return footer; }
        public void setFooter(Map<String, Object> footer) { this.footer = footer; }
    }
}