package com.library.management.service.report;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.library.management.dto.ReportRequest;
import com.library.management.entity.Book;
import com.library.management.entity.ReportHistory;
import com.library.management.service.report.data.ReportDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF帳票生成サービス
 * 書籍一覧レポートのPDF出力を担当
 */
@Service
@Primary
public class PDFReportService extends ReportService {

    @Autowired
    private ReportDataService reportDataService;

    @Autowired
    private TemplateEngine templateEngine;

    private static final String FONT_PATH = "fonts/NotoSansCJK-Regular.ttc,0";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    protected String doGenerateReport(Long userId, ReportRequest request, ReportHistory history) throws Exception {
        logger.info("PDF帳票生成開始: userId={}, reportType={}", userId, request.getReportType());
        String filePath = generateFilePath(request.getFormat(), request.getReportType());
        logger.debug("生成ファイルパス: {}", filePath);

        try {
            switch (request.getReportType().toUpperCase()) {
                case "PERSONAL":
                    logger.debug("個人統計PDF生成開始");
                    generatePersonalStatisticsPDF(userId, request, filePath);
                    break;
                case "SYSTEM":
                    logger.debug("全体統計PDF生成開始");
                    generateSystemStatisticsPDF(userId, request, filePath);
                    break;
                case "BOOK_LIST":
                    logger.debug("書籍一覧PDF生成開始");
                    generateBookListPDF(userId, request, filePath);
                    break;
                default:
                    throw new IllegalArgumentException("サポートされていないレポートタイプ: " + request.getReportType());
            }

            logger.info("PDF帳票生成完了: filePath={}", filePath);
            return filePath;
        } catch (Exception e) {
            logger.error("PDF帳票生成エラー: userId={}, reportType={}, filePath={}", userId, request.getReportType(), filePath, e);
            throw e;
        }
    }

    /**
     * 個人統計PDFレポート生成
     */
    private void generatePersonalStatisticsPDF(Long userId, ReportRequest request, String filePath) throws Exception {
        // 個人の書籍データ取得
        List<Book> books = reportDataService.getFilteredBooks(userId, request);
        ReportDataService.BookStatistics statistics = reportDataService.getBookStatistics(userId, request.getFilters());

        generateStatisticsPDF(books, statistics, request, filePath, userId, "個人統計レポート");
    }

    /**
     * 全体統計PDFレポート生成
     */
    private void generateSystemStatisticsPDF(Long userId, ReportRequest request, String filePath) throws Exception {
        // 全体の書籍データ取得（管理者のみ）
        List<Book> books = reportDataService.getFilteredBooks(null, request); // null = 全ユーザー
        ReportDataService.BookStatistics statistics = reportDataService.getBookStatistics(null, request.getFilters());

        generateStatisticsPDF(books, statistics, request, filePath, userId, "全体統計レポート");
    }

    /**
     * 書籍一覧PDFレポート生成
     */
    private void generateBookListPDF(Long userId, ReportRequest request, String filePath) throws Exception {
        // データ取得
        List<Book> books = reportDataService.getFilteredBooks(userId, request);

        // PDF生成方式の選択
        if (useHtmlTemplate(request)) {
            generateFromHtmlTemplate(books, request, filePath, userId);
        } else {
            generateDirectPDF(books, request, filePath, userId);
        }
    }

    /**
     * HTMLテンプレートからPDF生成
     */
    private void generateFromHtmlTemplate(List<Book> books, ReportRequest request,
                                        String filePath, Long userId) throws Exception {
        // Thymeleafコンテキスト作成
        Context context = new Context();
        context.setVariable("books", books);
        context.setVariable("reportTitle", "書籍一覧レポート");
        context.setVariable("generatedDate", LocalDateTime.now().format(DATE_FORMATTER));
        context.setVariable("totalCount", books.size());
        context.setVariable("filters", request.getFilters());

        // HTMLレンダリング
        String htmlContent = templateEngine.process("reports/book-list", context);

        // PDF変換設定
        ConverterProperties properties = new ConverterProperties();
        try {
            PdfFont font = PdfFontFactory.createFont(FONT_PATH, PdfEncodings.IDENTITY_H);
            properties.setFontProvider(new com.itextpdf.html2pdf.resolver.font.DefaultFontProvider(true, false, false));
        } catch (Exception e) {
            logger.warn("日本語フォントの読み込みに失敗、デフォルトフォントを使用: {}", e.getMessage());
        }

        // PDF生成
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            HtmlConverter.convertToPdf(htmlContent, fos, properties);
        }

        logger.info("HTMLテンプレートからPDF生成完了: {}", filePath);
    }

    /**
     * 直接PDF生成（iTextを使用）
     */
    private void generateDirectPDF(List<Book> books, ReportRequest request,
                                 String filePath, Long userId) throws Exception {
        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, PageSize.A4)) {

            // メタデータ設定
            setPDFMetadata(pdfDoc);

            // フォント設定
            PdfFont font = getJapaneseFont();
            if (font != null) {
                document.setFont(font);
            }

            // ヘッダー追加
            addHeader(document, "書籍一覧レポート");

            // サマリー情報
            addSummaryInfo(document, books, request);

            // 書籍一覧テーブル
            addBookListTable(document, books);

            // フッター追加
            addFooter(document);

            logger.info("直接PDF生成完了: {}", filePath);
        }
    }

    /**
     * PDFメタデータ設定
     */
    private void setPDFMetadata(PdfDocument pdfDoc) {
        PdfDocumentInfo info = pdfDoc.getDocumentInfo();
        info.setTitle("蔵書管理システム - 書籍一覧レポート");
        info.setAuthor("蔵書管理システム");
        info.setCreator("Library Management System");
        info.setSubject("書籍一覧");
        info.setKeywords("書籍, レポート, 蔵書管理");
        info.setProducer("iText 7.2.5");
    }

    /**
     * 日本語フォント取得
     */
    private PdfFont getJapaneseFont() {
        try {
            return PdfFontFactory.createFont(FONT_PATH, PdfEncodings.IDENTITY_H);
        } catch (Exception e) {
            logger.warn("日本語フォントの読み込みに失敗、システムフォントを試行");
            try {
                return PdfFontFactory.createFont("HeiseiKakuGo-W5", "UniJIS-UCS2-H");
            } catch (Exception ex) {
                logger.warn("システムフォントも失敗、デフォルトフォントを使用");
                return null;
            }
        }
    }

    /**
     * ヘッダー追加
     */
    private void addHeader(Document document, String title) {
        Paragraph header = new Paragraph(title)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(18)
            .setBold()
            .setMarginBottom(10);
        document.add(header);

        Paragraph dateInfo = new Paragraph("生成日時: " + LocalDateTime.now().format(DATE_FORMATTER))
            .setTextAlignment(TextAlignment.RIGHT)
            .setFontSize(10)
            .setMarginBottom(20);
        document.add(dateInfo);
    }

    /**
     * サマリー情報追加
     */
    private void addSummaryInfo(Document document, List<Book> books, ReportRequest request) {
        Paragraph summary = new Paragraph("総件数: " + books.size() + "件")
            .setFontSize(12)
            .setBold()
            .setMarginBottom(15);
        document.add(summary);

        // フィルター情報
        if (request.getFilters() != null) {
            addFilterInfo(document, request.getFilters());
        }
    }

    /**
     * フィルター情報追加
     */
    private void addFilterInfo(Document document, ReportRequest.ReportFilters filters) {
        List<String> filterTexts = new java.util.ArrayList<>();

        if (filters.getReadStatus() != null && !filters.getReadStatus().isEmpty()) {
            filterTexts.add("読書状況: " + String.join(", ", filters.getReadStatus()));
        }
        if (filters.getPublisher() != null && !filters.getPublisher().isEmpty()) {
            filterTexts.add("出版社: " + filters.getPublisher());
        }
        if (filters.getStartDate() != null || filters.getEndDate() != null) {
            String dateRange = "登録期間: ";
            if (filters.getStartDate() != null) {
                dateRange += filters.getStartDate().toString();
            }
            dateRange += " ～ ";
            if (filters.getEndDate() != null) {
                dateRange += filters.getEndDate().toString();
            }
            filterTexts.add(dateRange);
        }

        if (!filterTexts.isEmpty()) {
            Paragraph filterInfo = new Paragraph("フィルター条件:")
                .setFontSize(10)
                .setBold()
                .setMarginBottom(5);
            document.add(filterInfo);

            for (String filterText : filterTexts) {
                Paragraph filter = new Paragraph("  • " + filterText)
                    .setFontSize(10)
                    .setMarginBottom(2);
                document.add(filter);
            }

            document.add(new Paragraph().setMarginBottom(10));
        }
    }

    /**
     * 書籍一覧テーブル追加
     */
    private void addBookListTable(Document document, List<Book> books) {
        if (books.isEmpty()) {
            document.add(new Paragraph("表示する書籍がありません。")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20));
            return;
        }

        // テーブル作成（列幅指定）
        float[] columnWidths = {1, 4, 3, 2, 1.5f, 1.5f};
        Table table = new Table(UnitValue.createPercentArray(columnWidths))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(20);

        // ヘッダー行
        String[] headers = {"No.", "タイトル", "著者", "出版社", "読書状況", "登録日"};
        for (String header : headers) {
            Cell cell = new Cell()
                .add(new Paragraph(header))
                .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(5);
            table.addHeaderCell(cell);
        }

        // データ行
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);

            table.addCell(createCell(String.valueOf(i + 1), TextAlignment.CENTER));
            table.addCell(createCell(book.getTitle(), TextAlignment.LEFT));
            table.addCell(createCell(getAuthorsString(book), TextAlignment.LEFT));
            table.addCell(createCell(book.getPublisher() != null ? book.getPublisher() : "", TextAlignment.LEFT));
            table.addCell(createCell(book.getReadStatus() != null ? book.getReadStatus().getName() : "", TextAlignment.CENTER));
            table.addCell(createCell(book.getCreatedAt() != null ? book.getCreatedAt().format(dateFormatter) : "", TextAlignment.CENTER));
        }

        document.add(table);
    }

    /**
     * テーブルセル作成
     */
    private Cell createCell(String content, TextAlignment alignment) {
        return new Cell()
            .add(new Paragraph(content != null ? content : ""))
            .setTextAlignment(alignment)
            .setPadding(3)
            .setFontSize(9);
    }

    /**
     * 著者名文字列取得
     */
    private String getAuthorsString(Book book) {
        if (book.getBookAuthors() == null || book.getBookAuthors().isEmpty()) {
            return "";
        }
        return book.getBookAuthors().stream()
            .map(bookAuthor -> bookAuthor.getAuthor().getName())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    /**
     * フッター追加
     */
    private void addFooter(Document document) {
        Paragraph footer = new Paragraph("蔵書管理システム - Generated by Library Management System")
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(8)
            .setMarginTop(20);
        document.add(footer);
    }

    /**
     * 統計PDF生成（共通処理）
     */
    private void generateStatisticsPDF(List<Book> books, ReportDataService.BookStatistics statistics,
                                     ReportRequest request, String filePath, Long userId, String title) throws Exception {
        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, PageSize.A4)) {

            // メタデータ設定
            setPDFMetadata(pdfDoc);

            // フォント設定
            PdfFont font = getJapaneseFont();
            if (font != null) {
                document.setFont(font);
            }

            // ヘッダー追加
            addHeader(document, title);

            // 統計情報追加
            addStatisticsInfo(document, statistics);

            // 書籍一覧テーブル（上位20件）
            List<Book> limitedBooks = books.stream().limit(20).collect(java.util.stream.Collectors.toList());
            if (!limitedBooks.isEmpty()) {
                document.add(new Paragraph("書籍一覧（上位20件）")
                    .setFontSize(14)
                    .setBold()
                    .setMarginTop(20)
                    .setMarginBottom(10));
                addBookListTable(document, limitedBooks);
            }

            // フッター追加
            addFooter(document);

            logger.info("統計PDF生成完了: {}", filePath);
        }
    }

    /**
     * 統計情報追加
     */
    private void addStatisticsInfo(Document document, ReportDataService.BookStatistics statistics) {
        // 総計
        Paragraph summary = new Paragraph("総書籍数: " + statistics.getTotalCount() + "冊")
            .setFontSize(14)
            .setBold()
            .setMarginBottom(15);
        document.add(summary);

        // 読書状況別統計
        if (!statistics.getStatusCounts().isEmpty()) {
            document.add(new Paragraph("読書状況別統計")
                .setFontSize(12)
                .setBold()
                .setMarginBottom(10));

            Table statusTable = new Table(2)
                .setWidth(UnitValue.createPercentValue(50))
                .setMarginBottom(15);

            statusTable.addHeaderCell(createHeaderCell("状況"));
            statusTable.addHeaderCell(createHeaderCell("冊数"));

            statistics.getStatusCounts().forEach((status, count) -> {
                statusTable.addCell(createCell(status, TextAlignment.LEFT));
                statusTable.addCell(createCell(count + "冊", TextAlignment.CENTER));
            });

            document.add(statusTable);
        }

        // 出版社別統計
        if (!statistics.getPublisherCounts().isEmpty()) {
            document.add(new Paragraph("出版社別統計（上位5社）")
                .setFontSize(12)
                .setBold()
                .setMarginBottom(10));

            Table publisherTable = new Table(2)
                .setWidth(UnitValue.createPercentValue(60))
                .setMarginBottom(20);

            publisherTable.addHeaderCell(createHeaderCell("出版社"));
            publisherTable.addHeaderCell(createHeaderCell("冊数"));

            statistics.getPublisherCounts().forEach((publisher, count) -> {
                publisherTable.addCell(createCell(publisher, TextAlignment.LEFT));
                publisherTable.addCell(createCell(count + "冊", TextAlignment.CENTER));
            });

            document.add(publisherTable);
        }
    }

    /**
     * ヘッダーセル作成
     */
    private Cell createHeaderCell(String content) {
        return new Cell()
            .add(new Paragraph(content))
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(5);
    }

    /**
     * HTMLテンプレート使用判定
     */
    private boolean useHtmlTemplate(ReportRequest request) {
        // カスタムオプションでテンプレート使用が指定されている場合
        if (request.getOptions() != null &&
            request.getOptions().getCustomOptions() != null) {
            Object useTemplate = request.getOptions().getCustomOptions().get("useHtmlTemplate");
            if (useTemplate instanceof Boolean) {
                return (Boolean) useTemplate;
            }
        }

        // デフォルトはダイレクト生成
        return false;
    }
}