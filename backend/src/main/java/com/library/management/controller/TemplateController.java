package com.library.management.controller;

import com.library.management.entity.ReportTemplate;
import com.library.management.service.report.template.TemplateService;
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
 * 帳票テンプレートコントローラー
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private static final Logger logger = LoggerFactory.getLogger(TemplateController.class);

    @Autowired
    private TemplateService templateService;

    /**
     * テンプレート一覧取得
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTemplates(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            List<ReportTemplate> templates = templateService.getAccessibleTemplates(userId, type, category);

            response.put("success", true);
            response.put("templates", templates.stream().map(this::convertToDto).toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("テンプレート一覧取得エラー", e);
            response.put("success", false);
            response.put("message", "テンプレート一覧の取得に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * テンプレート作成
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(
            @Valid @RequestBody TemplateCreateRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            ReportTemplate template = templateService.createTemplate(
                request.getName(),
                request.getType(),
                request.getCategory(),
                request.getTemplateData(),
                userId
            );

            response.put("success", true);
            response.put("message", "テンプレートを作成しました");
            response.put("template", convertToDto(template));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("テンプレート作成リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("テンプレート作成エラー", e);
            response.put("success", false);
            response.put("message", "テンプレートの作成に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * テンプレート更新
     */
    @PutMapping("/{templateId}")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody TemplateUpdateRequest request,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);

            ReportTemplate template = templateService.updateTemplate(
                templateId,
                request.getName(),
                request.getTemplateData(),
                userId
            );

            response.put("success", true);
            response.put("message", "テンプレートを更新しました");
            response.put("template", convertToDto(template));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("テンプレート更新リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("テンプレート更新エラー", e);
            response.put("success", false);
            response.put("message", "テンプレートの更新に失敗しました");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * テンプレート削除
     */
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(
            @PathVariable Long templateId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long userId = getUserId(authentication);
            templateService.deleteTemplate(templateId, userId);

            response.put("success", true);
            response.put("message", "テンプレートを削除しました");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("テンプレート削除リクエストエラー: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("テンプレート削除エラー", e);
            response.put("success", false);
            response.put("message", "テンプレートの削除に失敗しました");
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
     * テンプレートDTO変換
     */
    private Map<String, Object> convertToDto(ReportTemplate template) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", template.getId());
        dto.put("name", template.getName());
        dto.put("type", template.getType());
        dto.put("category", template.getCategory());
        dto.put("templateData", templateService.parseTemplateData(template.getTemplateData()));
        dto.put("isDefault", template.getIsDefault());
        dto.put("isOwner", template.getUserId() != null);
        dto.put("createdAt", template.getCreatedAt());
        dto.put("updatedAt", template.getUpdatedAt());
        return dto;
    }

    /**
     * テンプレート作成リクエスト
     */
    public static class TemplateCreateRequest {
        private String name;
        private String type;
        private String category;
        private Map<String, Object> templateData;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public Map<String, Object> getTemplateData() { return templateData; }
        public void setTemplateData(Map<String, Object> templateData) { this.templateData = templateData; }
    }

    /**
     * テンプレート更新リクエスト
     */
    public static class TemplateUpdateRequest {
        private String name;
        private Map<String, Object> templateData;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Map<String, Object> getTemplateData() { return templateData; }
        public void setTemplateData(Map<String, Object> templateData) { this.templateData = templateData; }
    }
}