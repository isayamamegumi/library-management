package com.library.management.service.report.data;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.Book;
import com.library.management.entity.ReadStatus;
import com.library.management.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帳票用データ取得サービス
 * 帳票生成に必要なデータの取得・フィルタリングを担当
 */
@Service
public class ReportDataService {

    private static final Logger logger = LoggerFactory.getLogger(ReportDataService.class);

    @Autowired
    private BookRepository bookRepository;

    /**
     * フィルター条件に基づく書籍データ取得
     */
    @Transactional(readOnly = true)
    public List<Book> getFilteredBooks(Long userId, ReportRequest request) {
        try {
            logger.info("書籍データ取得開始: userId={}, reportType={}", userId, request != null ? request.getReportType() : "null");

            Specification<Book> spec = createBookSpecification(userId, request != null ? request.getFilters() : null);
            Sort sort = createSort(request != null ? request.getOptions() : null);

            // ページング制限（大量データ対策）
            Pageable pageable = PageRequest.of(0, getMaxRecords(request), sort);
            logger.debug("検索条件設定完了: maxRecords={}", getMaxRecords(request));

            List<Book> books = bookRepository.findAll(spec, pageable).getContent();
            logger.info("書籍データ取得完了: userId={}, 取得件数={}", userId, books.size());

            return books;
        } catch (Exception e) {
            logger.error("書籍データ取得エラー: userId={}", userId, e);
            throw e;
        }
    }

    /**
     * 書籍検索条件作成
     */
    private Specification<Book> createBookSpecification(Long userId, ReportRequest.ReportFilters filters) {
        return (Root<Book> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ユーザーID条件（userIdがnullの場合は全ユーザー対象）
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }

            if (filters != null) {
                // 読書状況フィルター
                if (filters.getReadStatus() != null && !filters.getReadStatus().isEmpty()) {
                    List<Predicate> statusPredicates = new ArrayList<>();
                    for (String status : filters.getReadStatus()) {
                        statusPredicates.add(cb.equal(root.get("readStatus").get("name"), status));
                    }
                    predicates.add(cb.or(statusPredicates.toArray(new Predicate[0])));
                }

                // 出版社フィルター
                if (filters.getPublisher() != null && !filters.getPublisher().trim().isEmpty()) {
                    predicates.add(cb.like(cb.lower(root.get("publisher")),
                        "%" + filters.getPublisher().toLowerCase() + "%"));
                }

                // 著者フィルター
                if (filters.getAuthor() != null && !filters.getAuthor().trim().isEmpty()) {
                    Join<Object, Object> authorsJoin = root.join("authors", JoinType.LEFT);
                    predicates.add(cb.like(cb.lower(authorsJoin.get("name")),
                        "%" + filters.getAuthor().toLowerCase() + "%"));
                }

                // ジャンルフィルター
                if (filters.getGenre() != null && !filters.getGenre().trim().isEmpty()) {
                    predicates.add(cb.like(cb.lower(root.get("genre")),
                        "%" + filters.getGenre().toLowerCase() + "%"));
                }

                // 登録日範囲フィルター
                if (filters.getStartDate() != null) {
                    LocalDateTime startDateTime = filters.getStartDate().atStartOfDay();
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDateTime));
                }
                if (filters.getEndDate() != null) {
                    LocalDateTime endDateTime = filters.getEndDate().atTime(23, 59, 59);
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDateTime));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * ソート条件作成
     */
    private Sort createSort(ReportRequest.ReportOptions options) {
        if (options == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String sortBy = options.getSortBy() != null ? options.getSortBy() : "createdAt";
        String sortOrder = options.getSortOrder() != null ? options.getSortOrder() : "DESC";

        // ソートフィールドのマッピング
        String actualSortBy = mapSortField(sortBy);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ?
            Sort.Direction.ASC : Sort.Direction.DESC;

        return Sort.by(direction, actualSortBy);
    }

    /**
     * ソートフィールドマッピング
     */
    private String mapSortField(String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "title":
                return "title";
            case "publisher":
                return "publisher";
            case "created_at":
            case "createdAt":
                return "createdAt";
            case "updated_at":
            case "updatedAt":
                return "updatedAt";
            default:
                return "createdAt";
        }
    }

    /**
     * 最大取得件数設定
     */
    private int getMaxRecords(ReportRequest request) {
        if (request != null && request.getOptions() != null &&
            request.getOptions().getCustomOptions() != null) {
            Object maxRecords = request.getOptions().getCustomOptions().get("maxRecords");
            if (maxRecords instanceof Integer) {
                int max = (Integer) maxRecords;
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(this.getClass());
                logger.info("カスタム制限値を使用: maxRecords={}", max);
                return Math.min(max, 10000); // 最大10,000件制限
            }
        }

        // SYSTEM権限の場合は制限を緩和
        if (request != null && "SYSTEM".equalsIgnoreCase(request.getReportType())) {
            logger.info("システム統計レポート用制限値を使用: maxRecords=1000");
            return 1000; // 管理者向けは1000件に制限してパフォーマンス改善
        }

        // デフォルトは制限なし（実際には10,000件まで）
        return 10000;
    }

    /**
     * 全書籍データ取得（管理者用）
     */
    public List<Book> getAllBooks(ReportRequest request) {
        try {
            logger.info("全書籍データ取得開始");

            Specification<Book> spec = createBookSpecification(null, request != null ? request.getFilters() : null);
            Sort sort = createSort(request != null ? request.getOptions() : null);

            Pageable pageable = PageRequest.of(0, getMaxRecords(request), sort);
            List<Book> books = bookRepository.findAll(spec, pageable).getContent();

            logger.info("全書籍データ取得完了: 取得件数={}", books.size());
            return books;

        } catch (Exception e) {
            logger.error("全書籍データ取得エラー", e);
            throw e;
        }
    }

    /**
     * システム統計取得
     */
    public SystemStatistics getSystemStatistics(ReportRequest.ReportFilters filters) {
        try {
            logger.info("システム統計取得開始");

            // 全体統計
            long totalBooks = bookRepository.count();
            long totalUsers = bookRepository.countDistinctUsers();

            // ステータス別統計
            Map<String, Long> statusCounts = bookRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    book -> book.getReadStatus().toString(),
                    java.util.stream.Collectors.counting()
                ));

            // 出版社別統計
            Map<String, Long> publisherCounts = bookRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    Book::getPublisher,
                    java.util.stream.Collectors.counting()
                ));

            SystemStatistics statistics = new SystemStatistics(
                totalBooks, totalUsers, statusCounts, publisherCounts
            );

            logger.info("システム統計取得完了: totalBooks={}, totalUsers={}", totalBooks, totalUsers);
            return statistics;

        } catch (Exception e) {
            logger.error("システム統計取得エラー", e);
            throw e;
        }
    }

    /**
     * ユーザー別統計取得
     */
    public List<UserStatistics> getUserStatistics(ReportRequest.ReportFilters filters) {
        try {
            logger.info("ユーザー別統計取得開始");

            // ユーザー別の書籍数を集計
            List<Object[]> userStats = bookRepository.findUserStatistics();

            List<UserStatistics> statistics = userStats.stream()
                .map(row -> new UserStatistics(
                    (Long) row[0],      // userId
                    (String) row[1],    // userName
                    (Long) row[2]       // bookCount
                ))
                .collect(java.util.stream.Collectors.toList());

            logger.info("ユーザー別統計取得完了: userCount={}", statistics.size());
            return statistics;

        } catch (Exception e) {
            logger.error("ユーザー別統計取得エラー", e);
            throw e;
        }
    }

    /**
     * 書籍統計データ取得
     */
    @Transactional(readOnly = true)
    public BookStatistics getBookStatistics(Long userId, ReportRequest.ReportFilters filters) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(this.getClass());

        try {
            logger.info("書籍統計データ取得開始: userId={}", userId);

            List<Book> books = getFilteredBooks(userId, createRequestForStatistics(filters));
            logger.debug("統計対象書籍数: {}", books.size());

            BookStatistics stats = new BookStatistics();
            stats.setTotalCount(books.size());

            // 読書状況別集計
            logger.debug("読書状況別集計開始");
            books.stream()
                .filter(book -> book.getReadStatus() != null)
                .forEach(book -> {
                    String status = book.getReadStatus().getName();
                    logger.debug("読書状況: {} - 書籍: {}", status, book.getTitle());
                    stats.incrementStatusCount(status);
                });

            logger.info("読書状況別集計結果: {}", stats.getStatusCounts());

            // 出版社別集計（上位5社）
            books.stream()
                .filter(book -> book.getPublisher() != null && !book.getPublisher().trim().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(
                    Book::getPublisher,
                    java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> stats.addPublisherCount(entry.getKey(), entry.getValue().intValue()));

            logger.info("書籍統計データ取得完了: userId={}, 総数={}, 状況別数={}, 出版社数={}",
                userId, stats.getTotalCount(), stats.getStatusCounts().size(), stats.getPublisherCounts().size());

            return stats;
        } catch (Exception e) {
            logger.error("書籍統計データ取得エラー: userId={}", userId, e);
            throw e;
        }
    }

    /**
     * 統計用リクエスト作成
     */
    private ReportRequest createRequestForStatistics(ReportRequest.ReportFilters filters) {
        ReportRequest request = new ReportRequest("STATISTICS", "DATA");
        request.setFilters(filters);

        // 統計用の最大件数設定
        ReportRequest.ReportOptions options = new ReportRequest.ReportOptions();
        options.setCustomOptions(java.util.Map.of("maxRecords", 10000));
        request.setOptions(options);

        return request;
    }

    /**
     * 書籍統計データクラス
     */
    public static class BookStatistics {
        private int totalCount;
        private java.util.Map<String, Integer> statusCounts = new java.util.HashMap<>();
        private java.util.Map<String, Integer> publisherCounts = new java.util.LinkedHashMap<>();

        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

        public java.util.Map<String, Integer> getStatusCounts() { return statusCounts; }
        public void setStatusCounts(java.util.Map<String, Integer> statusCounts) { this.statusCounts = statusCounts; }

        public java.util.Map<String, Integer> getPublisherCounts() { return publisherCounts; }
        public void setPublisherCounts(java.util.Map<String, Integer> publisherCounts) { this.publisherCounts = publisherCounts; }

        public void incrementStatusCount(String status) {
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
        }

        public void addPublisherCount(String publisher, int count) {
            publisherCounts.put(publisher, count);
        }
    }

    /**
     * システム統計クラス
     */
    public static class SystemStatistics {
        private final long totalBooks;
        private final long totalUsers;
        private final Map<String, Long> statusCounts;
        private final Map<String, Long> publisherCounts;

        public SystemStatistics(long totalBooks, long totalUsers,
                               Map<String, Long> statusCounts, Map<String, Long> publisherCounts) {
            this.totalBooks = totalBooks;
            this.totalUsers = totalUsers;
            this.statusCounts = statusCounts;
            this.publisherCounts = publisherCounts;
        }

        public long getTotalBooks() { return totalBooks; }
        public long getTotalUsers() { return totalUsers; }
        public Map<String, Long> getStatusCounts() { return statusCounts; }
        public Map<String, Long> getPublisherCounts() { return publisherCounts; }
    }

    /**
     * ユーザー統計クラス
     */
    public static class UserStatistics {
        private final Long userId;
        private final String userName;
        private final Long bookCount;

        public UserStatistics(Long userId, String userName, Long bookCount) {
            this.userId = userId;
            this.userName = userName;
            this.bookCount = bookCount;
        }

        public Long getUserId() { return userId; }
        public String getUserName() { return userName; }
        public Long getBookCount() { return bookCount; }
    }
}