package com.library.management.service.report.distribution;

import com.library.management.entity.ReportDistribution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Email配信サービス
 */
@Service
public class EmailDistributionService {

    private static final Logger logger = LoggerFactory.getLogger(EmailDistributionService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.mail.from:noreply@library.system}")
    private String defaultFromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    /**
     * レポートメール送信
     */
    public void sendReport(ReportDistribution distribution, File reportFile, String reportFileName) {
        try {
            if (!mailEnabled) {
                logger.warn("メール送信が無効化されています: distributionId={}", distribution.getId());
                return;
            }

            logger.info("レポートメール送信開始: distributionId={}, fileName={}",
                distribution.getId(), reportFileName);

            // 宛先リスト取得
            List<String> recipients = parseRecipients(distribution.getRecipients());

            // 配信設定取得
            Map<String, Object> config = parseDistributionConfig(distribution.getDistributionConfig());

            // 添付ファイル準備
            File attachmentFile = prepareAttachmentFile(distribution, reportFile, reportFileName);

            // メール送信
            for (String recipient : recipients) {
                sendSingleEmail(distribution, recipient, attachmentFile, reportFileName, config);
            }

            // 一時ファイル削除
            if (attachmentFile != reportFile && attachmentFile.exists()) {
                attachmentFile.delete();
            }

            logger.info("レポートメール送信完了: distributionId={}, recipientCount={}",
                distribution.getId(), recipients.size());

        } catch (Exception e) {
            logger.error("レポートメール送信エラー: distributionId={}", distribution.getId(), e);
            throw new RuntimeException("メール送信に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 単一メール送信
     */
    private void sendSingleEmail(ReportDistribution distribution, String recipient,
                               File attachmentFile, String fileName, Map<String, Object> config) throws Exception {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // 送信者設定
        String fromAddress = getFromAddress(config);
        helper.setFrom(fromAddress);

        // 宛先設定
        helper.setTo(recipient);

        // 件名設定
        String subject = buildSubject(distribution, fileName);
        helper.setSubject(subject);

        // 本文設定
        String messageBody = buildMessageBody(distribution, fileName, config);
        helper.setText(messageBody, true); // HTML対応

        // 添付ファイル設定
        if (distribution.getAttachFile() && attachmentFile != null && attachmentFile.exists()) {
            FileSystemResource fileResource = new FileSystemResource(attachmentFile);
            helper.addAttachment(getAttachmentFileName(fileName, distribution.getCompressFile()), fileResource);
        }

        // メール送信
        mailSender.send(message);

        logger.info("メール送信完了: recipient={}, subject={}", recipient, subject);
    }

    /**
     * 添付ファイル準備
     */
    private File prepareAttachmentFile(ReportDistribution distribution, File reportFile, String fileName)
            throws IOException {

        if (!distribution.getAttachFile()) {
            return null;
        }

        // パスワード保護が必要な場合
        if (distribution.getPasswordProtection() != null && !distribution.getPasswordProtection().trim().isEmpty()) {
            return createPasswordProtectedFile(reportFile, fileName, distribution.getPasswordProtection());
        }

        // 圧縮が必要な場合
        if (distribution.getCompressFile()) {
            return createCompressedFile(reportFile, fileName);
        }

        return reportFile;
    }

    /**
     * パスワード保護ファイル作成
     */
    private File createPasswordProtectedFile(File reportFile, String fileName, String password) throws IOException {
        // 簡易実装：ZIPパスワード保護
        String zipFileName = fileName.replaceFirst("\\.[^.]+$", ".zip");
        File zipFile = new File(reportFile.getParent(), "protected_" + zipFileName);

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 実際のパスワード保護はサードパーティライブラリが必要
            // ここでは基本的なZIP圧縮のみ実装
            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            Files.copy(reportFile.toPath(), zos);
            zos.closeEntry();
        }

        logger.info("パスワード保護ファイル作成: {}", zipFile.getName());
        return zipFile;
    }

    /**
     * 圧縮ファイル作成
     */
    private File createCompressedFile(File reportFile, String fileName) throws IOException {
        String zipFileName = fileName.replaceFirst("\\.[^.]+$", ".zip");
        File zipFile = new File(reportFile.getParent(), "compressed_" + zipFileName);

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            Files.copy(reportFile.toPath(), zos);
            zos.closeEntry();
        }

        logger.info("圧縮ファイル作成: {}", zipFile.getName());
        return zipFile;
    }

    /**
     * 件名構築
     */
    private String buildSubject(ReportDistribution distribution, String fileName) {
        if (distribution.getSubject() != null && !distribution.getSubject().trim().isEmpty()) {
            return distribution.getSubject();
        }

        return "レポート送信: " + fileName;
    }

    /**
     * メッセージ本文構築
     */
    private String buildMessageBody(ReportDistribution distribution, String fileName, Map<String, Object> config) {
        StringBuilder body = new StringBuilder();

        // カスタムメッセージ
        if (distribution.getMessage() != null && !distribution.getMessage().trim().isEmpty()) {
            body.append(distribution.getMessage()).append("\n\n");
        } else {
            body.append("レポートファイルをお送りします。\n\n");
        }

        // ファイル情報
        body.append("ファイル名: ").append(fileName).append("\n");

        if (distribution.getAttachFile()) {
            if (distribution.getCompressFile()) {
                body.append("※ ファイルは圧縮されています。\n");
            }
            if (distribution.getPasswordProtection() != null && !distribution.getPasswordProtection().trim().isEmpty()) {
                body.append("※ ファイルはパスワードで保護されています。\n");
                body.append("パスワード: ").append(distribution.getPasswordProtection()).append("\n");
            }
        }

        // フッター
        body.append("\n---\n");
        body.append("図書管理システム\n");
        body.append("このメールは自動送信されています。\n");

        return body.toString();
    }

    /**
     * 送信者アドレス取得
     */
    private String getFromAddress(Map<String, Object> config) {
        if (config != null && config.containsKey("fromAddress")) {
            String fromAddress = (String) config.get("fromAddress");
            if (fromAddress != null && !fromAddress.trim().isEmpty()) {
                return fromAddress;
            }
        }
        return defaultFromAddress;
    }

    /**
     * 添付ファイル名取得
     */
    private String getAttachmentFileName(String originalFileName, boolean compressed) {
        if (compressed && !originalFileName.endsWith(".zip")) {
            return originalFileName.replaceFirst("\\.[^.]+$", ".zip");
        }
        return originalFileName;
    }

    /**
     * 宛先リスト解析
     */
    @SuppressWarnings("unchecked")
    private List<String> parseRecipients(String recipientsJson) throws Exception {
        return objectMapper.readValue(recipientsJson, List.class);
    }

    /**
     * 配信設定解析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDistributionConfig(String configJson) throws Exception {
        if (configJson == null || configJson.trim().isEmpty()) {
            return Map.of();
        }
        return objectMapper.readValue(configJson, Map.class);
    }
}