package com.library.management.service.report.distribution;

import com.library.management.entity.ReportDistribution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slack配信サービス
 */
@Service
public class SlackDistributionService {

    private static final Logger logger = LoggerFactory.getLogger(SlackDistributionService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.slack.enabled:false}")
    private boolean slackEnabled;

    @Value("${app.slack.default-webhook-url:}")
    private String defaultWebhookUrl;

    /**
     * SlackレポートChattootsukaiつalertType送信
     */
    public void sendReport(ReportDistribution distribution, File reportFile, String reportFileName) {
        try {
            if (!slackEnabled) {
                logger.warn("Slack送信が無効化されています: distributionId={}", distribution.getId());
                return;
            }

            logger.info("Slackレポート送信開始: distributionId={}, fileName={}",
                distribution.getId(), reportFileName);

            // 宛先リスト取得
            List<String> recipients = parseRecipients(distribution.getRecipients());

            // 配信設定取得
            Map<String, Object> config = parseDistributionConfig(distribution.getDistributionConfig());

            // Webhook URL取得
            String webhookUrl = getWebhookUrl(config);

            // メッセージ送信
            for (String recipient : recipients) {
                sendSlackMessage(distribution, recipient, reportFileName, webhookUrl, config);
            }

            logger.info("Slackレポート送信完了: distributionId={}, recipientCount={}",
                distribution.getId(), recipients.size());

        } catch (Exception e) {
            logger.error("Slackレポート送信エラー: distributionId={}", distribution.getId(), e);
            throw new RuntimeException("Slack送信に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * Slackメッセージ送信
     */
    private void sendSlackMessage(ReportDistribution distribution, String channel,
                                String fileName, String webhookUrl, Map<String, Object> config) {
        try {
            // メッセージペイロード構築
            Map<String, Object> payload = buildSlackPayload(distribution, channel, fileName, config);

            // HTTP ヘッダー設定
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // リクエスト作成
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // Slack Webhook送信
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Slackメッセージ送信完了: channel={}, fileName={}", channel, fileName);
            } else {
                logger.warn("Slackメッセージ送信警告: channel={}, status={}, response={}",
                    channel, response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            logger.error("Slackメッセージ送信エラー: channel={}", channel, e);
            throw e;
        }
    }

    /**
     * Slackペイロード構築
     */
    private Map<String, Object> buildSlackPayload(ReportDistribution distribution, String channel,
                                                String fileName, Map<String, Object> config) {
        Map<String, Object> payload = new HashMap<>();

        // チャンネル設定
        if (channel.startsWith("#") || channel.startsWith("@")) {
            payload.put("channel", channel);
        }

        // ボット名設定
        String botName = (String) config.getOrDefault("botName", "図書管理システム");
        payload.put("username", botName);

        // アイコン設定
        String iconEmoji = (String) config.getOrDefault("iconEmoji", ":books:");
        if (iconEmoji != null && !iconEmoji.trim().isEmpty()) {
            payload.put("icon_emoji", iconEmoji);
        }

        // メッセージ本文構築
        String text = buildSlackMessage(distribution, fileName, config);
        payload.put("text", text);

        // 添付ファイル情報（Slack Attachments形式）
        if (distribution.getAttachFile()) {
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", "good");
            attachment.put("title", "レポートファイル");
            attachment.put("text", "ファイル名: " + fileName);

            if (distribution.getCompressFile()) {
                attachment.put("footer", "※ ファイルは圧縮されています");
            }

            payload.put("attachments", List.of(attachment));
        }

        return payload;
    }

    /**
     * Slackメッセージ本文構築
     */
    private String buildSlackMessage(ReportDistribution distribution, String fileName, Map<String, Object> config) {
        StringBuilder message = new StringBuilder();

        // タイトル
        message.append(":page_facing_up: **レポート生成完了**\n\n");

        // カスタムメッセージ
        if (distribution.getMessage() != null && !distribution.getMessage().trim().isEmpty()) {
            message.append(distribution.getMessage()).append("\n\n");
        }

        // ファイル情報
        message.append("**ファイル名:** ").append(fileName).append("\n");
        message.append("**配信名:** ").append(distribution.getName()).append("\n");

        // 配信オプション情報
        if (distribution.getAttachFile()) {
            message.append("**添付:** あり\n");

            if (distribution.getCompressFile()) {
                message.append("**圧縮:** あり\n");
            }

            if (distribution.getPasswordProtection() != null && !distribution.getPasswordProtection().trim().isEmpty()) {
                message.append("**パスワード保護:** あり\n");
            }
        } else {
            message.append("**添付:** なし（通知のみ）\n");
        }

        // 生成時刻
        message.append("**生成時刻:** ").append(java.time.LocalDateTime.now().toString()).append("\n");

        return message.toString();
    }

    /**
     * Webhook URL取得
     */
    private String getWebhookUrl(Map<String, Object> config) {
        if (config != null && config.containsKey("webhookUrl")) {
            String webhookUrl = (String) config.get("webhookUrl");
            if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                return webhookUrl;
            }
        }

        if (defaultWebhookUrl != null && !defaultWebhookUrl.trim().isEmpty()) {
            return defaultWebhookUrl;
        }

        throw new IllegalArgumentException("Slack Webhook URLが設定されていません");
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

    /**
     * Slackファイルアップロード
     * 注意: この機能を使用するにはSlack APIトークンが必要
     */
    public void uploadFileToSlack(String channel, File file, String title, String token) {
        try {
            logger.info("Slackファイルアップロード開始: channel={}, file={}", channel, file.getName());

            // Slack Files API URL
            String uploadUrl = "https://slack.com/api/files.upload";

            // マルチパートフォームデータの作成は省略
            // 実装にはSpring WebのMultiValueMapやApache HttpClientなどを使用

            logger.warn("Slackファイルアップロード機能は未実装です。通知のみ送信されました。");

        } catch (Exception e) {
            logger.error("Slackファイルアップロードエラー: channel={}", channel, e);
            throw new RuntimeException("Slackファイルアップロードに失敗しました", e);
        }
    }
}