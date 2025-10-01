package com.library.management.service.report.template;

import com.library.management.entity.ReportTemplate;
import com.library.management.repository.ReportTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 帳票テンプレートサービス
 */
@Service
@Transactional
public class TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);

    @Autowired
    private ReportTemplateRepository templateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * ユーザーがアクセス可能なテンプレート一覧取得
     */
    public List<ReportTemplate> getAccessibleTemplates(Long userId, String type, String category) {
        try {
            List<ReportTemplate> templates = templateRepository.findAccessibleTemplates(userId, type, category);
            logger.info("テンプレート一覧取得成功: userId={}, type={}, category={}, count={}",
                userId, type, category, templates.size());
            return templates;
        } catch (Exception e) {
            logger.error("テンプレート一覧取得エラー: userId={}, type={}, category={}",
                userId, type, category, e);
            throw new RuntimeException("テンプレート一覧の取得に失敗しました", e);
        }
    }

    /**
     * デフォルトテンプレート取得
     */
    public Optional<ReportTemplate> getDefaultTemplate(String type, String category) {
        try {
            Optional<ReportTemplate> template = templateRepository
                .findByTypeAndCategoryAndIsDefaultTrueAndIsActiveTrue(type, category);

            if (template.isPresent()) {
                logger.info("デフォルトテンプレート取得成功: type={}, category={}, templateId={}",
                    type, category, template.get().getId());
            } else {
                logger.warn("デフォルトテンプレートが見つかりません: type={}, category={}", type, category);
            }

            return template;
        } catch (Exception e) {
            logger.error("デフォルトテンプレート取得エラー: type={}, category={}", type, category, e);
            throw new RuntimeException("デフォルトテンプレートの取得に失敗しました", e);
        }
    }

    /**
     * テンプレート作成
     */
    public ReportTemplate createTemplate(String name, String type, String category,
                                       Map<String, Object> templateData, Long userId) {
        try {
            // 同名チェック
            if (templateRepository.existsByNameAndUserIdAndIsActiveTrue(name, userId)) {
                throw new IllegalArgumentException("同名のテンプレートが既に存在します: " + name);
            }

            // JSON変換
            String templateDataJson = objectMapper.writeValueAsString(templateData);

            // テンプレート作成
            ReportTemplate template = new ReportTemplate(name, type, category, templateDataJson);
            template.setUserId(userId);

            ReportTemplate savedTemplate = templateRepository.save(template);
            logger.info("テンプレート作成成功: name={}, type={}, category={}, userId={}, templateId={}",
                name, type, category, userId, savedTemplate.getId());

            return savedTemplate;

        } catch (Exception e) {
            logger.error("テンプレート作成エラー: name={}, type={}, category={}, userId={}",
                name, type, category, userId, e);
            throw new RuntimeException("テンプレートの作成に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * テンプレート更新
     */
    public ReportTemplate updateTemplate(Long templateId, String name,
                                       Map<String, Object> templateData, Long userId) {
        try {
            Optional<ReportTemplate> templateOpt = templateRepository.findById(templateId);
            if (templateOpt.isEmpty()) {
                throw new IllegalArgumentException("テンプレートが見つかりません: " + templateId);
            }

            ReportTemplate template = templateOpt.get();

            // 権限チェック（自分のテンプレートのみ更新可能）
            if (!userId.equals(template.getUserId())) {
                throw new IllegalArgumentException("テンプレートの更新権限がありません");
            }

            // 同名チェック（自分以外）
            if (!template.getName().equals(name) &&
                templateRepository.existsByNameAndUserIdAndIsActiveTrue(name, userId)) {
                throw new IllegalArgumentException("同名のテンプレートが既に存在します: " + name);
            }

            // 更新
            template.setName(name);
            template.setTemplateData(objectMapper.writeValueAsString(templateData));

            ReportTemplate updatedTemplate = templateRepository.save(template);
            logger.info("テンプレート更新成功: templateId={}, name={}, userId={}",
                templateId, name, userId);

            return updatedTemplate;

        } catch (Exception e) {
            logger.error("テンプレート更新エラー: templateId={}, name={}, userId={}",
                templateId, name, userId, e);
            throw new RuntimeException("テンプレートの更新に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * テンプレート削除（論理削除）
     */
    public void deleteTemplate(Long templateId, Long userId) {
        try {
            Optional<ReportTemplate> templateOpt = templateRepository.findById(templateId);
            if (templateOpt.isEmpty()) {
                throw new IllegalArgumentException("テンプレートが見つかりません: " + templateId);
            }

            ReportTemplate template = templateOpt.get();

            // 権限チェック
            if (!userId.equals(template.getUserId())) {
                throw new IllegalArgumentException("テンプレートの削除権限がありません");
            }

            // 論理削除
            template.setIsActive(false);
            templateRepository.save(template);

            logger.info("テンプレート削除成功: templateId={}, userId={}", templateId, userId);

        } catch (Exception e) {
            logger.error("テンプレート削除エラー: templateId={}, userId={}", templateId, userId, e);
            throw new RuntimeException("テンプレートの削除に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * テンプレートデータ解析
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseTemplateData(String templateDataJson) {
        try {
            if (templateDataJson == null || templateDataJson.trim().isEmpty()) {
                return Map.of();
            }
            return objectMapper.readValue(templateDataJson, Map.class);
        } catch (Exception e) {
            logger.error("テンプレートデータ解析エラー: {}", templateDataJson, e);
            return Map.of();
        }
    }

    /**
     * デフォルトテンプレート初期化
     */
    @Transactional
    public void initializeDefaultTemplates() {
        try {
            // PDF書籍一覧テンプレート
            if (!templateRepository.existsByTypeAndCategoryAndIsDefaultTrueAndIsActiveTrue("PDF", "BOOK_LIST")) {
                createDefaultPdfBookListTemplate();
            }

            // Excel読書統計テンプレート
            if (!templateRepository.existsByTypeAndCategoryAndIsDefaultTrueAndIsActiveTrue("EXCEL", "READING_STATS")) {
                createDefaultExcelReadingStatsTemplate();
            }

            logger.info("デフォルトテンプレート初期化完了");

        } catch (Exception e) {
            logger.error("デフォルトテンプレート初期化エラー", e);
        }
    }

    /**
     * デフォルトPDF書籍一覧テンプレート作成
     */
    private void createDefaultPdfBookListTemplate() throws Exception {
        Map<String, Object> templateData = Map.of(
            "layout", Map.of(
                "pageSize", "A4",
                "orientation", "portrait",
                "margins", Map.of("top", 20, "bottom", 20, "left", 15, "right", 15)
            ),
            "header", Map.of(
                "title", "書籍一覧レポート",
                "showDate", true,
                "showUserName", true
            ),
            "content", Map.of(
                "columns", List.of(
                    Map.of("field", "title", "label", "タイトル", "width", 40),
                    Map.of("field", "author", "label", "著者", "width", 25),
                    Map.of("field", "publisher", "label", "出版社", "width", 20),
                    Map.of("field", "readStatus", "label", "ステータス", "width", 15)
                ),
                "fontSize", 9,
                "rowHeight", 20
            ),
            "footer", Map.of(
                "showPageNumber", true,
                "showGeneratedBy", true
            )
        );

        ReportTemplate template = new ReportTemplate(
            "標準PDF書籍一覧", "PDF", "BOOK_LIST",
            objectMapper.writeValueAsString(templateData)
        );
        template.setIsDefault(true);
        templateRepository.save(template);
    }

    /**
     * デフォルトExcel読書統計テンプレート作成
     */
    private void createDefaultExcelReadingStatsTemplate() throws Exception {
        Map<String, Object> templateData = Map.of(
            "sheets", List.of(
                Map.of(
                    "name", "概要",
                    "content", Map.of(
                        "title", "読書統計概要",
                        "sections", List.of("総計", "月次推移", "ジャンル別", "出版社別")
                    )
                ),
                Map.of(
                    "name", "詳細データ",
                    "content", Map.of(
                        "columns", List.of(
                            Map.of("field", "title", "label", "タイトル"),
                            Map.of("field", "author", "label", "著者"),
                            Map.of("field", "readDate", "label", "読了日"),
                            Map.of("field", "rating", "label", "評価")
                        )
                    )
                )
            ),
            "charts", Map.of(
                "monthlyProgress", Map.of("type", "line", "title", "月次読書推移"),
                "genreDistribution", Map.of("type", "pie", "title", "ジャンル分布")
            ),
            "formatting", Map.of(
                "headerStyle", Map.of("bold", true, "backgroundColor", "#4CAF50"),
                "dataStyle", Map.of("fontSize", 10, "border", true)
            )
        );

        ReportTemplate template = new ReportTemplate(
            "標準Excel読書統計", "EXCEL", "READING_STATS",
            objectMapper.writeValueAsString(templateData)
        );
        template.setIsDefault(true);
        templateRepository.save(template);
    }
}