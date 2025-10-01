package com.library.management.service.report;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.Book;
import com.library.management.entity.ReportHistory;
import com.library.management.service.report.data.ReportDataService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel帳票生成サービス
 * 書籍一覧レポートのExcel出力を担当
 */
@Service
public class ExcelReportService extends ReportService {

    @Autowired
    private ReportDataService reportDataService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
    private static final DateTimeFormatter DATE_CELL_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // Excel最適化設定
    private static final int EXCEL_MAX_ROWS_PER_SHEET = 65000; // Excel制限より少し少なめに設定
    private static final int LARGE_DATASET_THRESHOLD = 1000;   // 大量データの閾値
    private static final String CHART_SHEET_NAME = "📊 グラフ";
    private static final String SUMMARY_SHEET_NAME = "📋 サマリー";
    private static final String DETAILED_SHEET_NAME = "📈 詳細統計";
    private static final String BOOKLIST_SHEET_NAME = "📚 書籍一覧";

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    protected String doGenerateReport(Long userId, ReportRequest request, ReportHistory history) throws Exception {
        logger.info("Excel帳票生成開始: userId={}, reportType={}", userId, request.getReportType());
        long startTime = System.currentTimeMillis();

        String filePath = generateFilePath(request.getFormat(), request.getReportType());

        try {
            // メモリ最適化: 大量データの場合は事前に警告
            if (request.getReportType().equalsIgnoreCase("SYSTEM")) {
                logger.info("システム全体レポート生成中 - 大量データ処理のため時間がかかる場合があります");
            }

            switch (request.getReportType().toUpperCase()) {
                case "PERSONAL":
                    generatePersonalStatisticsExcel(userId, request, filePath);
                    break;
                case "SYSTEM":
                    generateSystemStatisticsExcel(userId, request, filePath);
                    break;
                case "BOOK_LIST":
                    generateBookListExcel(userId, request, filePath);
                    break;
                default:
                    throw new IllegalArgumentException("サポートされていないレポートタイプ: " + request.getReportType());
            }

            long endTime = System.currentTimeMillis();
            logger.info("Excel帳票生成完了: filePath={}, 処理時間={}ms", filePath, (endTime - startTime));

            return filePath;
        } catch (Exception e) {
            logger.error("Excel帳票生成エラー: userId={}, reportType={}, filePath={}", userId, request.getReportType(), filePath, e);
            throw new RuntimeException("Excel帳票生成に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 個人統計Excelレポート生成
     */
    private void generatePersonalStatisticsExcel(Long userId, ReportRequest request, String filePath) throws Exception {
        List<Book> books = reportDataService.getFilteredBooks(userId, request);
        ReportDataService.BookStatistics statistics = reportDataService.getBookStatistics(userId, request.getFilters());
        generateStatisticsExcel(books, statistics, request, filePath, "個人統計レポート");
    }

    /**
     * 全体統計Excelレポート生成
     */
    private void generateSystemStatisticsExcel(Long userId, ReportRequest request, String filePath) throws Exception {
        List<Book> books = reportDataService.getFilteredBooks(null, request);
        ReportDataService.BookStatistics statistics = reportDataService.getBookStatistics(null, request.getFilters());
        generateStatisticsExcel(books, statistics, request, filePath, "全体統計レポート");
    }

    /**
     * 書籍一覧Excelレポート生成
     */
    private void generateBookListExcel(Long userId, ReportRequest request, String filePath) throws Exception {
        // データ取得
        List<Book> books = reportDataService.getFilteredBooks(userId, request);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {

            // ワークシート作成
            Sheet sheet = workbook.createSheet("書籍一覧");

            // スタイル設定
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle dateCellStyle = createDateCellStyle(workbook);
            CellStyle centerStyle = createCenterStyle(workbook);

            int rowIndex = 0;

            // タイトル行
            rowIndex = addTitle(sheet, rowIndex, titleStyle, "書籍一覧レポート");

            // 生成日時
            rowIndex = addGenerationDate(sheet, rowIndex, dataStyle);

            // サマリー情報
            rowIndex = addSummaryInfo(sheet, rowIndex, dataStyle, books, request);

            // フィルター情報
            if (request.getFilters() != null) {
                rowIndex = addFilterInfo(sheet, rowIndex, dataStyle, request.getFilters());
            }

            // 空行
            rowIndex++;

            // ヘッダー行
            rowIndex = addHeader(sheet, rowIndex, headerStyle);

            // データ行
            rowIndex = addDataRows(sheet, rowIndex, dataStyle, dateCellStyle, centerStyle, books);

            // 列幅自動調整
            autoSizeColumns(sheet);

            // Excel保存
            workbook.write(fos);

            logger.info("Excel帳票生成完了: {}", filePath);
        }
    }

    /**
     * タイトル行追加
     */
    private int addTitle(Sheet sheet, int rowIndex, CellStyle titleStyle, String title) {
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);

        // タイトルセルをマージ（A列からF列まで）
        sheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 5));

        return rowIndex + 1; // 空行追加
    }

    /**
     * 生成日時追加
     */
    private int addGenerationDate(Sheet sheet, int rowIndex, CellStyle dataStyle) {
        Row dateRow = sheet.createRow(rowIndex++);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("生成日時: " + LocalDateTime.now().format(DATE_FORMATTER));
        dateCell.setCellStyle(dataStyle);

        return rowIndex + 1; // 空行追加
    }

    /**
     * サマリー情報追加
     */
    private int addSummaryInfo(Sheet sheet, int rowIndex, CellStyle dataStyle,
                              List<Book> books, ReportRequest request) {
        Row summaryRow = sheet.createRow(rowIndex++);
        Cell summaryCell = summaryRow.createCell(0);
        summaryCell.setCellValue("総件数: " + books.size() + "件");
        summaryCell.setCellStyle(dataStyle);

        return rowIndex;
    }

    /**
     * フィルター情報追加
     */
    private int addFilterInfo(Sheet sheet, int rowIndex, CellStyle dataStyle,
                             ReportRequest.ReportFilters filters) {
        Row filterHeaderRow = sheet.createRow(rowIndex++);
        Cell filterHeaderCell = filterHeaderRow.createCell(0);
        filterHeaderCell.setCellValue("フィルター条件:");
        filterHeaderCell.setCellStyle(dataStyle);

        if (filters.getReadStatus() != null && !filters.getReadStatus().isEmpty()) {
            Row row = sheet.createRow(rowIndex++);
            Cell cell = row.createCell(0);
            cell.setCellValue("  • 読書状況: " + String.join(", ", filters.getReadStatus()));
            cell.setCellStyle(dataStyle);
        }

        if (filters.getPublisher() != null && !filters.getPublisher().isEmpty()) {
            Row row = sheet.createRow(rowIndex++);
            Cell cell = row.createCell(0);
            cell.setCellValue("  • 出版社: " + filters.getPublisher());
            cell.setCellStyle(dataStyle);
        }

        if (filters.getStartDate() != null || filters.getEndDate() != null) {
            Row row = sheet.createRow(rowIndex++);
            Cell cell = row.createCell(0);
            String dateRange = "  • 登録期間: ";
            if (filters.getStartDate() != null) {
                dateRange += filters.getStartDate().toString();
            }
            dateRange += " ～ ";
            if (filters.getEndDate() != null) {
                dateRange += filters.getEndDate().toString();
            }
            cell.setCellValue(dateRange);
            cell.setCellStyle(dataStyle);
        }

        return rowIndex;
    }

    /**
     * ヘッダー行追加
     */
    private int addHeader(Sheet sheet, int rowIndex, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(rowIndex++);
        String[] headers = {"No.", "タイトル", "著者", "出版社", "読書状況", "登録日"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        return rowIndex;
    }

    /**
     * データ行追加
     */
    private int addDataRows(Sheet sheet, int rowIndex, CellStyle dataStyle,
                           CellStyle dateCellStyle, CellStyle centerStyle, List<Book> books) {
        if (books.isEmpty()) {
            Row noDataRow = sheet.createRow(rowIndex++);
            Cell noDataCell = noDataRow.createCell(0);
            noDataCell.setCellValue("表示する書籍がありません。");
            noDataCell.setCellStyle(dataStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 5));
            return rowIndex;
        }

        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            Row dataRow = sheet.createRow(rowIndex++);

            // No.
            Cell noCell = dataRow.createCell(0);
            noCell.setCellValue(i + 1);
            noCell.setCellStyle(centerStyle);

            // タイトル
            Cell titleCell = dataRow.createCell(1);
            titleCell.setCellValue(book.getTitle());
            titleCell.setCellStyle(dataStyle);

            // 著者
            Cell authorCell = dataRow.createCell(2);
            authorCell.setCellValue(getAuthorsString(book));
            authorCell.setCellStyle(dataStyle);

            // 出版社
            Cell publisherCell = dataRow.createCell(3);
            publisherCell.setCellValue(book.getPublisher() != null ? book.getPublisher() : "");
            publisherCell.setCellStyle(dataStyle);

            // 読書状況
            Cell statusCell = dataRow.createCell(4);
            statusCell.setCellValue(book.getReadStatus() != null ? book.getReadStatus().getName() : "");
            statusCell.setCellStyle(centerStyle);

            // 登録日
            Cell dateCell = dataRow.createCell(5);
            if (book.getCreatedAt() != null) {
                dateCell.setCellValue(book.getCreatedAt().format(DATE_CELL_FORMATTER));
            }
            dateCell.setCellStyle(dateCellStyle);
        }

        return rowIndex;
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
     * 列幅自動調整
     */
    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
            // 最大幅制限（文字数 * 256）
            int currentWidth = sheet.getColumnWidth(i);
            int maxWidth = 50 * 256; // 50文字相当
            if (currentWidth > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
    }

    /**
     * ヘッダー用スタイル作成
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * タイトル用スタイル作成
     */
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * データ用スタイル作成
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 日付セル用スタイル作成
     */
    private CellStyle createDateCellStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * 中央揃えスタイル作成
     */
    private CellStyle createCenterStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * 数値用スタイル作成
     */
    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0"));
        return style;
    }

    /**
     * パーセント用スタイル作成
     */
    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.0%"));
        return style;
    }

    /**
     * 条件付き書式用スタイル作成（高い値）
     */
    private CellStyle createHighValueStyle(Workbook workbook) {
        CellStyle style = createNumberStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 条件付き書式用スタイル作成（低い値）
     */
    private CellStyle createLowValueStyle(Workbook workbook) {
        CellStyle style = createNumberStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 統計Excelレポート生成（複数シート構成・最適化版）
     */
    private void generateStatisticsExcel(List<Book> books, ReportDataService.BookStatistics statistics,
                                       ReportRequest request, String filePath, String title) throws Exception {

        // Excel生成設定の検証
        ExcelStyleHelper.validateExcelConfiguration(books.size());

        // メモリ効率化：大量データの場合はチャンク処理
        boolean isLargeDataset = books.size() > LARGE_DATASET_THRESHOLD;

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {

            // ワークブック設定最適化
            optimizeWorkbookSettings(workbook);

            // スタイル設定（ワークブック全体で共有）
            ExcelStyleHelper styleHelper = new ExcelStyleHelper(workbook);

            logger.info("Excel生成: シート数=4, 書籍数={}, 大量データ={}", books.size(), isLargeDataset);

            // 1. サマリーシート作成
            createOptimizedSummarySheet(workbook, title, statistics, styleHelper);

            // 2. 詳細統計シート作成
            createOptimizedDetailedStatisticsSheet(workbook, statistics, styleHelper);

            // 3. 書籍一覧シート作成（チャンク処理対応）
            createOptimizedBookListSheet(workbook, books, styleHelper, isLargeDataset);

            // 4. グラフシート作成
            createOptimizedChartsSheet(workbook, statistics, styleHelper);

            // ワークブック最終最適化
            finalizeWorkbook(workbook);

            // Excel保存
            workbook.write(fos);

            logger.info("最適化複数シート統計Excel生成完了: filePath={}, ファイルサイズ={}bytes",
                       filePath, new java.io.File(filePath).length());
        }
    }

    /**
     * ワークブック設定最適化
     */
    private void optimizeWorkbookSettings(XSSFWorkbook workbook) {
        // 計算モードを手動に設定（大量データ処理時の最適化）
        workbook.setForceFormulaRecalculation(true);

        // 共有文字列テーブル最適化（Apache POIバージョンによっては利用できない場合があります）
        // workbook.getSharedStringSource().setEntryCount(0);
    }

    /**
     * ワークブック最終最適化
     */
    private void finalizeWorkbook(XSSFWorkbook workbook) {
        // アクティブシートをサマリーに設定
        workbook.setActiveSheet(0);

        // 各シートの表示設定最適化
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            sheet.setSelected(i == 0); // 最初のシートのみ選択状態
        }
    }

    /**
     * Excelスタイルヘルパークラス
     */
    private static class ExcelStyleHelper {
        private final CellStyle headerStyle;
        private final CellStyle titleStyle;
        private final CellStyle dataStyle;
        private final CellStyle dateCellStyle;
        private final CellStyle centerStyle;
        private final CellStyle numberStyle;
        private final CellStyle percentStyle;
        private final CellStyle highValueStyle;
        private final CellStyle lowValueStyle;

        public ExcelStyleHelper(Workbook workbook) {
            // パフォーマンス最適化：スタイル作成時間を測定
            long styleStartTime = System.currentTimeMillis();

            this.headerStyle = createHeaderStyle(workbook);
            this.titleStyle = createTitleStyle(workbook);
            this.dataStyle = createDataStyle(workbook);
            this.dateCellStyle = createDateCellStyle(workbook);
            this.centerStyle = createCenterStyle(workbook);
            this.numberStyle = createNumberStyle(workbook);
            this.percentStyle = createPercentStyle(workbook);
            this.highValueStyle = createHighValueStyle(workbook);
            this.lowValueStyle = createLowValueStyle(workbook);

            long styleCreationTime = System.currentTimeMillis() - styleStartTime;
            if (styleCreationTime > 100) { // 100ms以上かかった場合は警告
                System.out.println("警告: Excelスタイル作成に" + styleCreationTime + "ms かかりました");
            }
        }

        /**
         * Excel生成設定の検証
         */
        public static void validateExcelConfiguration(int dataSize) {
            if (dataSize > EXCEL_MAX_ROWS_PER_SHEET) {
                throw new IllegalArgumentException(
                    String.format("データサイズ(%d)がExcelの最大行数制限(%d)を超えています",
                    dataSize, EXCEL_MAX_ROWS_PER_SHEET));
            }

            if (dataSize > LARGE_DATASET_THRESHOLD) {
                System.out.println(String.format(
                    "大量データ処理モード: %d件のデータを処理します（閾値: %d件）",
                    dataSize, LARGE_DATASET_THRESHOLD));
            }
        }

        // Getterメソッド
        public CellStyle getHeaderStyle() { return headerStyle; }
        public CellStyle getTitleStyle() { return titleStyle; }
        public CellStyle getDataStyle() { return dataStyle; }
        public CellStyle getDateCellStyle() { return dateCellStyle; }
        public CellStyle getCenterStyle() { return centerStyle; }
        public CellStyle getNumberStyle() { return numberStyle; }
        public CellStyle getPercentStyle() { return percentStyle; }
        public CellStyle getHighValueStyle() { return highValueStyle; }
        public CellStyle getLowValueStyle() { return lowValueStyle; }

        // 既存スタイル作成メソッドをstaticに変更（workbookを引数で受け取る）
        private static CellStyle createHeaderStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 12);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        private static CellStyle createTitleStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 16);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle createDataStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setFontHeightInPoints((short) 10);
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        private static CellStyle createDateCellStyle(Workbook workbook) {
            CellStyle style = createDataStyle(workbook);
            style.setAlignment(HorizontalAlignment.CENTER);
            return style;
        }

        private static CellStyle createCenterStyle(Workbook workbook) {
            CellStyle style = createDataStyle(workbook);
            style.setAlignment(HorizontalAlignment.CENTER);
            return style;
        }

        private static CellStyle createNumberStyle(Workbook workbook) {
            CellStyle style = createDataStyle(workbook);
            style.setAlignment(HorizontalAlignment.RIGHT);
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("#,##0"));
            return style;
        }

        private static CellStyle createPercentStyle(Workbook workbook) {
            CellStyle style = createDataStyle(workbook);
            style.setAlignment(HorizontalAlignment.RIGHT);
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("0.0%"));
            return style;
        }

        private static CellStyle createHighValueStyle(Workbook workbook) {
            CellStyle style = createNumberStyle(workbook);
            style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static CellStyle createLowValueStyle(Workbook workbook) {
            CellStyle style = createNumberStyle(workbook);
            style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }
    }

    /**
     * 統計サマリー追加
     */
    private int addStatisticsSummary(Sheet sheet, int rowIndex, CellStyle dataStyle,
                                   ReportDataService.BookStatistics statistics) {
        Row summaryRow = sheet.createRow(rowIndex++);
        Cell summaryCell = summaryRow.createCell(0);
        summaryCell.setCellValue("総書籍数: " + statistics.getTotalCount() + "冊");
        summaryCell.setCellStyle(dataStyle);

        return rowIndex + 1; // 空行追加
    }

    /**
     * 読書状況別統計追加
     */
    private int addStatusStatistics(Sheet sheet, int rowIndex, CellStyle headerStyle,
                                  CellStyle dataStyle, CellStyle centerStyle,
                                  ReportDataService.BookStatistics statistics) {
        // セクションタイトル
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("読書状況別統計");
        titleCell.setCellStyle(headerStyle);

        // ヘッダー行
        Row headerRow = sheet.createRow(rowIndex++);
        Cell statusHeaderCell = headerRow.createCell(0);
        statusHeaderCell.setCellValue("状況");
        statusHeaderCell.setCellStyle(headerStyle);

        Cell countHeaderCell = headerRow.createCell(1);
        countHeaderCell.setCellValue("冊数");
        countHeaderCell.setCellStyle(headerStyle);

        // データ行
        for (java.util.Map.Entry<String, Integer> entry : statistics.getStatusCounts().entrySet()) {
            Row dataRow = sheet.createRow(rowIndex++);

            Cell statusCell = dataRow.createCell(0);
            statusCell.setCellValue(entry.getKey());
            statusCell.setCellStyle(dataStyle);

            Cell countCell = dataRow.createCell(1);
            countCell.setCellValue(entry.getValue() + "冊");
            countCell.setCellStyle(centerStyle);
        }

        return rowIndex + 1; // 空行追加
    }

    /**
     * 出版社別統計追加
     */
    private int addPublisherStatistics(Sheet sheet, int rowIndex, CellStyle headerStyle,
                                     CellStyle dataStyle, CellStyle centerStyle,
                                     ReportDataService.BookStatistics statistics) {
        // セクションタイトル
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("出版社別統計（上位5社）");
        titleCell.setCellStyle(headerStyle);

        // ヘッダー行
        Row headerRow = sheet.createRow(rowIndex++);
        Cell publisherHeaderCell = headerRow.createCell(0);
        publisherHeaderCell.setCellValue("出版社");
        publisherHeaderCell.setCellStyle(headerStyle);

        Cell countHeaderCell = headerRow.createCell(1);
        countHeaderCell.setCellValue("冊数");
        countHeaderCell.setCellStyle(headerStyle);

        // データ行
        for (java.util.Map.Entry<String, Integer> entry : statistics.getPublisherCounts().entrySet()) {
            Row dataRow = sheet.createRow(rowIndex++);

            Cell publisherCell = dataRow.createCell(0);
            publisherCell.setCellValue(entry.getKey());
            publisherCell.setCellStyle(dataStyle);

            Cell countCell = dataRow.createCell(1);
            countCell.setCellValue(entry.getValue() + "冊");
            countCell.setCellStyle(centerStyle);
        }

        return rowIndex + 1; // 空行追加
    }

    /**
     * 書籍一覧セクション追加
     */
    private int addBookListSection(Sheet sheet, int rowIndex, CellStyle headerStyle,
                                 CellStyle dataStyle, CellStyle dateCellStyle, CellStyle centerStyle,
                                 List<Book> books) {
        // セクションタイトル
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("書籍一覧（上位20件）");
        titleCell.setCellStyle(headerStyle);

        // 空行
        rowIndex++;

        // ヘッダー行
        rowIndex = addHeader(sheet, rowIndex, headerStyle);

        // データ行
        rowIndex = addDataRows(sheet, rowIndex, dataStyle, dateCellStyle, centerStyle, books);

        return rowIndex;
    }

    /**
     * サマリーシート作成
     */
    private void createSummarySheet(Workbook workbook, String title, ReportDataService.BookStatistics statistics,
                                  CellStyle headerStyle, CellStyle titleStyle, CellStyle dataStyle, CellStyle centerStyle) {
        Sheet sheet = workbook.createSheet(SUMMARY_SHEET_NAME);
        int rowIndex = 0;

        // タイトル行
        rowIndex = addTitle(sheet, rowIndex, titleStyle, title);

        // 生成日時
        rowIndex = addGenerationDate(sheet, rowIndex, dataStyle);

        // 総計情報
        Row totalRow = sheet.createRow(rowIndex++);
        Cell totalCell = totalRow.createCell(0);
        totalCell.setCellValue("📚 総書籍数");
        totalCell.setCellStyle(headerStyle);

        Cell totalValueCell = totalRow.createCell(1);
        totalValueCell.setCellValue(statistics.getTotalCount());
        totalValueCell.setCellStyle(centerStyle);

        rowIndex++; // 空行

        // 読書状況サマリー
        Row statusHeaderRow = sheet.createRow(rowIndex++);
        Cell statusHeaderCell = statusHeaderRow.createCell(0);
        statusHeaderCell.setCellValue("📖 読書状況サマリー");
        statusHeaderCell.setCellStyle(headerStyle);

        // 数式を使用した自動計算
        for (java.util.Map.Entry<String, Integer> entry : statistics.getStatusCounts().entrySet()) {
            Row statusRow = sheet.createRow(rowIndex++);

            Cell labelCell = statusRow.createCell(0);
            labelCell.setCellValue("  " + entry.getKey());
            labelCell.setCellStyle(dataStyle);

            Cell valueCell = statusRow.createCell(1);
            valueCell.setCellValue(entry.getValue());
            valueCell.setCellStyle(centerStyle);

            // パーセンテージ計算（数式）
            Cell percentCell = statusRow.createCell(2);
            if (statistics.getTotalCount() > 0) {
                percentCell.setCellFormula("B" + rowIndex + "/B$5");
                percentCell.setCellStyle(createPercentStyle(workbook));
            }
        }

        // 列幅調整
        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 詳細統計シート作成
     */
    private void createDetailedStatisticsSheet(Workbook workbook, ReportDataService.BookStatistics statistics,
                                             CellStyle headerStyle, CellStyle titleStyle, CellStyle dataStyle,
                                             CellStyle centerStyle, CellStyle numberStyle, CellStyle percentStyle) {
        Sheet sheet = workbook.createSheet(DETAILED_SHEET_NAME);
        int rowIndex = 0;

        // タイトル行
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("📈 詳細統計分析");
        titleCell.setCellStyle(titleStyle);

        rowIndex++; // 空行

        // 読書状況別詳細統計（条件付き書式付き）
        rowIndex = addDetailedStatusStatistics(sheet, rowIndex, statistics, headerStyle, dataStyle, numberStyle, percentStyle, workbook);

        rowIndex++; // 空行

        // 出版社別統計（条件付き書式付き）
        rowIndex = addDetailedPublisherStatistics(sheet, rowIndex, statistics, headerStyle, dataStyle, numberStyle, workbook);

        // 列幅調整
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 詳細読書状況統計追加
     */
    private int addDetailedStatusStatistics(Sheet sheet, int rowIndex, ReportDataService.BookStatistics statistics,
                                          CellStyle headerStyle, CellStyle dataStyle, CellStyle numberStyle,
                                          CellStyle percentStyle, Workbook workbook) {
        // ヘッダー
        Row headerRow = sheet.createRow(rowIndex++);
        String[] headers = {"読書状況", "冊数", "割合", "ランク", "備考"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // データ行（条件付き書式付き）
        int totalBooks = statistics.getTotalCount();
        int rank = 1;
        for (java.util.Map.Entry<String, Integer> entry : statistics.getStatusCounts().entrySet()) {
            Row row = sheet.createRow(rowIndex++);

            // 読書状況
            Cell statusCell = row.createCell(0);
            statusCell.setCellValue(entry.getKey());
            statusCell.setCellStyle(dataStyle);

            // 冊数
            Cell countCell = row.createCell(1);
            countCell.setCellValue(entry.getValue());
            // 条件付き書式適用
            if (entry.getValue() > totalBooks * 0.4) {
                countCell.setCellStyle(createHighValueStyle(workbook));
            } else if (entry.getValue() < totalBooks * 0.1) {
                countCell.setCellStyle(createLowValueStyle(workbook));
            } else {
                countCell.setCellStyle(numberStyle);
            }

            // 割合（数式）
            Cell percentCell = row.createCell(2);
            if (totalBooks > 0) {
                double percentage = (double) entry.getValue() / totalBooks;
                percentCell.setCellValue(percentage);
                percentCell.setCellStyle(percentStyle);
            }

            // ランク
            Cell rankCell = row.createCell(3);
            rankCell.setCellValue(rank++);
            rankCell.setCellStyle(numberStyle);

            // 備考
            Cell commentCell = row.createCell(4);
            if (entry.getValue() > totalBooks * 0.4) {
                commentCell.setCellValue("📈 高比率");
            } else if (entry.getValue() < totalBooks * 0.1) {
                commentCell.setCellValue("📉 低比率");
            } else {
                commentCell.setCellValue("📊 標準");
            }
            commentCell.setCellStyle(dataStyle);
        }

        return rowIndex;
    }

    /**
     * 詳細出版社統計追加
     */
    private int addDetailedPublisherStatistics(Sheet sheet, int rowIndex, ReportDataService.BookStatistics statistics,
                                             CellStyle headerStyle, CellStyle dataStyle, CellStyle numberStyle, Workbook workbook) {
        // セクションタイトル
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("🏢 出版社別分析");
        titleCell.setCellStyle(headerStyle);

        // ヘッダー
        Row headerRow = sheet.createRow(rowIndex++);
        String[] headers = {"出版社", "冊数", "市場シェア", "累積シェア"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // データ行
        int totalBooks = statistics.getTotalCount();
        double cumulativeShare = 0.0;
        for (java.util.Map.Entry<String, Integer> entry : statistics.getPublisherCounts().entrySet()) {
            Row row = sheet.createRow(rowIndex++);

            // 出版社名
            Cell publisherCell = row.createCell(0);
            publisherCell.setCellValue(entry.getKey());
            publisherCell.setCellStyle(dataStyle);

            // 冊数
            Cell countCell = row.createCell(1);
            countCell.setCellValue(entry.getValue());
            countCell.setCellStyle(numberStyle);

            // 市場シェア
            Cell shareCell = row.createCell(2);
            if (totalBooks > 0) {
                double share = (double) entry.getValue() / totalBooks;
                shareCell.setCellValue(share);
                shareCell.setCellStyle(createPercentStyle(workbook));
                cumulativeShare += share;
            }

            // 累積シェア
            Cell cumulativeCell = row.createCell(3);
            cumulativeCell.setCellValue(cumulativeShare);
            cumulativeCell.setCellStyle(createPercentStyle(workbook));
        }

        return rowIndex;
    }

    /**
     * 書籍一覧シート作成
     */
    private void createBookListSheet(Workbook workbook, List<Book> books, CellStyle headerStyle, CellStyle titleStyle,
                                   CellStyle dataStyle, CellStyle dateCellStyle, CellStyle centerStyle) {
        Sheet sheet = workbook.createSheet(BOOKLIST_SHEET_NAME);
        int rowIndex = 0;

        // タイトル行
        rowIndex = addTitle(sheet, rowIndex, titleStyle, "📚 書籍一覧詳細");

        // 空行
        rowIndex++;

        // ヘッダー行
        rowIndex = addHeader(sheet, rowIndex, headerStyle);

        // データ行
        rowIndex = addDataRows(sheet, rowIndex, dataStyle, dateCellStyle, centerStyle, books);

        // 列幅自動調整
        autoSizeColumns(sheet);
    }

    /**
     * グラフシート作成
     */
    private void createChartsSheet(Workbook workbook, ReportDataService.BookStatistics statistics,
                                 CellStyle headerStyle, CellStyle titleStyle) {
        XSSFSheet sheet = (XSSFSheet) workbook.createSheet(CHART_SHEET_NAME);
        int rowIndex = 0;

        // タイトル行
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("📊 統計グラフ");
        titleCell.setCellStyle(titleStyle);

        rowIndex += 2; // 空行

        // 1. 読書状況円グラフ用データ準備
        int dataStartRow = rowIndex;
        rowIndex = createChartDataForReadingStatus(sheet, rowIndex, statistics, headerStyle);

        // 円グラフ作成
        createPieChart(sheet, dataStartRow, rowIndex - 1, "読書状況分布");

        rowIndex += 15; // グラフ用スペース

        // 2. 出版社別棒グラフ用データ準備
        int publisherDataStartRow = rowIndex;
        rowIndex = createChartDataForPublishers(sheet, rowIndex, statistics, headerStyle);

        // 棒グラフ作成
        createBarChart(sheet, publisherDataStartRow, rowIndex - 1, "出版社別書籍数");

        // 列幅調整
        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 読書状況用チャートデータ作成
     */
    private int createChartDataForReadingStatus(Sheet sheet, int startRow, ReportDataService.BookStatistics statistics,
                                              CellStyle headerStyle) {
        int rowIndex = startRow;

        // ヘッダー行
        Row headerRow = sheet.createRow(rowIndex++);
        Cell statusHeaderCell = headerRow.createCell(0);
        statusHeaderCell.setCellValue("読書状況");
        statusHeaderCell.setCellStyle(headerStyle);

        Cell countHeaderCell = headerRow.createCell(1);
        countHeaderCell.setCellValue("冊数");
        countHeaderCell.setCellStyle(headerStyle);

        // データ行
        for (java.util.Map.Entry<String, Integer> entry : statistics.getStatusCounts().entrySet()) {
            Row dataRow = sheet.createRow(rowIndex++);

            Cell statusCell = dataRow.createCell(0);
            statusCell.setCellValue(entry.getKey());

            Cell countCell = dataRow.createCell(1);
            countCell.setCellValue(entry.getValue());
        }

        return rowIndex;
    }

    /**
     * 出版社用チャートデータ作成
     */
    private int createChartDataForPublishers(Sheet sheet, int startRow, ReportDataService.BookStatistics statistics,
                                           CellStyle headerStyle) {
        int rowIndex = startRow;

        // ヘッダー行
        Row headerRow = sheet.createRow(rowIndex++);
        Cell publisherHeaderCell = headerRow.createCell(0);
        publisherHeaderCell.setCellValue("出版社");
        publisherHeaderCell.setCellStyle(headerStyle);

        Cell countHeaderCell = headerRow.createCell(1);
        countHeaderCell.setCellValue("冊数");
        countHeaderCell.setCellStyle(headerStyle);

        // データ行（上位5社）
        int count = 0;
        for (java.util.Map.Entry<String, Integer> entry : statistics.getPublisherCounts().entrySet()) {
            if (count >= 5) break; // 上位5社のみ

            Row dataRow = sheet.createRow(rowIndex++);

            Cell publisherCell = dataRow.createCell(0);
            publisherCell.setCellValue(entry.getKey());

            Cell countCell = dataRow.createCell(1);
            countCell.setCellValue(entry.getValue());

            count++;
        }

        return rowIndex;
    }

    /**
     * 円グラフ作成
     */
    private void createPieChart(XSSFSheet sheet, int dataStartRow, int dataEndRow, String title) {
        try {
            // 描画オブジェクト作成
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, dataStartRow, 8, dataStartRow + 10);

            // チャート作成
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(title);

            // データソース設定
            XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(dataStartRow + 1, dataEndRow, 0, 0));
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(dataStartRow + 1, dataEndRow, 1, 1));

            // 円グラフ作成
            XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle("読書状況", null);

            // グラフに適用
            chart.plot(data);

            logger.info("円グラフ作成完了: {}", title);
        } catch (Exception e) {
            logger.warn("円グラフ作成でエラー: {}", e.getMessage());
        }
    }

    /**
     * 棒グラフ作成
     */
    private void createBarChart(XSSFSheet sheet, int dataStartRow, int dataEndRow, String title) {
        try {
            // 描画オブジェクト作成
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, dataStartRow, 8, dataStartRow + 10);

            // チャート作成
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(title);

            // 軸設定
            XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            bottomAxis.setTitle("出版社");
            XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
            leftAxis.setTitle("冊数");

            // データソース設定
            XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(dataStartRow + 1, dataEndRow, 0, 0));
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(dataStartRow + 1, dataEndRow, 1, 1));

            // 棒グラフ作成
            XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle("書籍数", null);

            // グラフに適用
            chart.plot(data);

            logger.info("棒グラフ作成完了: {}", title);
        } catch (Exception e) {
            logger.warn("棒グラフ作成でエラー: {}", e.getMessage());
        }
    }

    // ========================================
    // 最適化されたシート作成メソッド
    // ========================================

    private void createOptimizedSummarySheet(Workbook workbook, String title, ReportDataService.BookStatistics statistics,
                                            ExcelStyleHelper styleHelper) {
        createSummarySheet(workbook, title, statistics, styleHelper.getHeaderStyle(), styleHelper.getTitleStyle(),
                          styleHelper.getDataStyle(), styleHelper.getCenterStyle());
    }

    private void createOptimizedDetailedStatisticsSheet(Workbook workbook, ReportDataService.BookStatistics statistics,
                                                       ExcelStyleHelper styleHelper) {
        createDetailedStatisticsSheet(workbook, statistics, styleHelper.getHeaderStyle(), styleHelper.getTitleStyle(),
                                     styleHelper.getDataStyle(), styleHelper.getCenterStyle(), styleHelper.getNumberStyle(), styleHelper.getPercentStyle());
    }

    private void createOptimizedBookListSheet(Workbook workbook, List<Book> books, ExcelStyleHelper styleHelper, boolean isLargeDataset) {
        if (isLargeDataset) {
            // 大量データの場合は書籍数を制限
            List<Book> limitedBooks = books.stream().limit(1000).collect(java.util.stream.Collectors.toList());
            createBookListSheet(workbook, limitedBooks, styleHelper.getHeaderStyle(), styleHelper.getTitleStyle(),
                               styleHelper.getDataStyle(), styleHelper.getDateCellStyle(), styleHelper.getCenterStyle());
            logger.info("大量データのため書籍一覧を1000件に制限しました。元データ数: {}", books.size());
        } else {
            createBookListSheet(workbook, books, styleHelper.getHeaderStyle(), styleHelper.getTitleStyle(),
                               styleHelper.getDataStyle(), styleHelper.getDateCellStyle(), styleHelper.getCenterStyle());
        }
    }

    private void createOptimizedChartsSheet(Workbook workbook, ReportDataService.BookStatistics statistics, ExcelStyleHelper styleHelper) {
        createChartsSheet(workbook, statistics, styleHelper.getHeaderStyle(), styleHelper.getTitleStyle());
    }
}