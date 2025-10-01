package com.library.management.service.report.optimization;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.Book;
import com.library.management.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 大量データ帳票最適化サービス
 * 大量データを効率的に処理するための最適化機能を提供
 */
@Service
public class ReportOptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(ReportOptimizationService.class);

    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.report.optimization.batch-size:1000}")
    private int batchSize;

    @Value("${app.report.optimization.max-memory-records:10000}")
    private int maxMemoryRecords;

    @Value("${app.report.optimization.thread-pool-size:4}")
    private int threadPoolSize;

    @Value("${app.report.optimization.enable-parallel:true}")
    private boolean enableParallelProcessing;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    /**
     * 大量データの最適化取得
     */
    public OptimizedDataResult getOptimizedData(Long userId, ReportRequest request) {
        try {
            logger.info("大量データ最適化取得開始: userId={}, reportType={}", userId, request.getReportType());

            // データ量推定
            long estimatedCount = estimateDataCount(userId, request);
            logger.info("推定データ量: {} 件", estimatedCount);

            // 最適化戦略決定
            OptimizationStrategy strategy = determineOptimizationStrategy(estimatedCount);
            logger.info("選択された最適化戦略: {}", strategy);

            // 戦略に基づくデータ取得
            OptimizedDataResult result = executeOptimizedDataRetrieval(userId, request, strategy);

            logger.info("大量データ最適化取得完了: userId={}, actualCount={}, strategy={}",
                userId, result.getTotalRecords(), strategy);

            return result;

        } catch (Exception e) {
            logger.error("大量データ最適化取得エラー: userId={}", userId, e);
            throw new RuntimeException("大量データの取得に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * データ量推定
     */
    private long estimateDataCount(Long userId, ReportRequest request) {
        try {
            // 簡易クエリでカウント取得
            String countQuery = buildCountQuery(userId, request);
            Query query = entityManager.createQuery(countQuery);
            setQueryParameters(query, userId, request);

            Number count = (Number) query.getSingleResult();
            return count.longValue();

        } catch (Exception e) {
            logger.warn("データ量推定エラー、デフォルト値を使用: {}", e.getMessage());
            return bookRepository.countByUserId(userId);
        }
    }

    /**
     * 最適化戦略決定
     */
    private OptimizationStrategy determineOptimizationStrategy(long estimatedCount) {
        if (estimatedCount <= maxMemoryRecords) {
            return OptimizationStrategy.IN_MEMORY;
        } else if (estimatedCount <= maxMemoryRecords * 10) {
            return OptimizationStrategy.BATCH_PROCESSING;
        } else if (enableParallelProcessing && estimatedCount <= maxMemoryRecords * 50) {
            return OptimizationStrategy.PARALLEL_BATCH;
        } else {
            return OptimizationStrategy.STREAMING;
        }
    }

    /**
     * 最適化データ取得実行
     */
    private OptimizedDataResult executeOptimizedDataRetrieval(Long userId, ReportRequest request,
                                                             OptimizationStrategy strategy) throws Exception {
        switch (strategy) {
            case IN_MEMORY:
                return executeInMemoryStrategy(userId, request);
            case BATCH_PROCESSING:
                return executeBatchProcessingStrategy(userId, request);
            case PARALLEL_BATCH:
                return executeParallelBatchStrategy(userId, request);
            case STREAMING:
                return executeStreamingStrategy(userId, request);
            default:
                throw new IllegalArgumentException("サポートされていない最適化戦略: " + strategy);
        }
    }

    /**
     * インメモリ戦略実行
     */
    private OptimizedDataResult executeInMemoryStrategy(Long userId, ReportRequest request) {
        logger.debug("インメモリ戦略実行開始");

        long startTime = System.currentTimeMillis();

        // 通常のJPAクエリで全データ取得
        List<Book> books = getFilteredBooks(userId, request, null);

        long processingTime = System.currentTimeMillis() - startTime;

        logger.debug("インメモリ戦略実行完了: 件数={}, 処理時間={}ms", books.size(), processingTime);

        return new OptimizedDataResult(
            books,
            books.size(),
            OptimizationStrategy.IN_MEMORY,
            processingTime,
            1 // バッチ数
        );
    }

    /**
     * バッチ処理戦略実行
     */
    private OptimizedDataResult executeBatchProcessingStrategy(Long userId, ReportRequest request) {
        logger.debug("バッチ処理戦略実行開始: batchSize={}", batchSize);

        long startTime = System.currentTimeMillis();
        List<Book> allBooks = new ArrayList<>();
        int batchCount = 0;
        int page = 0;

        while (true) {
            Pageable pageable = PageRequest.of(page, batchSize);
            Page<Book> batchBooks = getFilteredBooksPage(userId, request, pageable);

            allBooks.addAll(batchBooks.getContent());
            batchCount++;

            logger.debug("バッチ {}完了: 件数={}", batchCount, batchBooks.getContent().size());

            // メモリクリア
            entityManager.clear();

            if (!batchBooks.hasNext()) {
                break;
            }
            page++;
        }

        long processingTime = System.currentTimeMillis() - startTime;

        logger.debug("バッチ処理戦略実行完了: 総件数={}, バッチ数={}, 処理時間={}ms",
            allBooks.size(), batchCount, processingTime);

        return new OptimizedDataResult(
            allBooks,
            allBooks.size(),
            OptimizationStrategy.BATCH_PROCESSING,
            processingTime,
            batchCount
        );
    }

    /**
     * 並列バッチ戦略実行
     */
    private OptimizedDataResult executeParallelBatchStrategy(Long userId, ReportRequest request) throws Exception {
        logger.debug("並列バッチ戦略実行開始: threadPoolSize={}", threadPoolSize);

        long startTime = System.currentTimeMillis();

        // 総データ数取得
        long totalCount = estimateDataCount(userId, request);
        int totalPages = (int) Math.ceil((double) totalCount / batchSize);

        // 並列バッチタスク作成
        List<Future<List<Book>>> futures = new ArrayList<>();

        for (int page = 0; page < totalPages; page++) {
            final int currentPage = page;
            Future<List<Book>> future = executorService.submit(() -> {
                try {
                    logger.debug("並列バッチ {}実行開始", currentPage);
                    Pageable pageable = PageRequest.of(currentPage, batchSize);
                    Page<Book> batchBooks = getFilteredBooksPage(userId, request, pageable);
                    logger.debug("並列バッチ {}実行完了: 件数={}", currentPage, batchBooks.getContent().size());
                    return batchBooks.getContent();
                } catch (Exception e) {
                    logger.error("並列バッチ {}実行エラー", currentPage, e);
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // 結果収集
        List<Book> allBooks = new ArrayList<>();
        for (Future<List<Book>> future : futures) {
            allBooks.addAll(future.get());
        }

        long processingTime = System.currentTimeMillis() - startTime;

        logger.debug("並列バッチ戦略実行完了: 総件数={}, バッチ数={}, 処理時間={}ms",
            allBooks.size(), totalPages, processingTime);

        return new OptimizedDataResult(
            allBooks,
            allBooks.size(),
            OptimizationStrategy.PARALLEL_BATCH,
            processingTime,
            totalPages
        );
    }

    /**
     * ストリーミング戦略実行
     */
    private OptimizedDataResult executeStreamingStrategy(Long userId, ReportRequest request) {
        logger.debug("ストリーミング戦略実行開始");

        long startTime = System.currentTimeMillis();

        // ストリーミング処理用の軽量データ構造
        List<Book> streamedBooks = new ArrayList<>();
        int batchCount = 0;
        int page = 0;

        // 必要最小限のフィールドのみ取得
        while (true) {
            Pageable pageable = PageRequest.of(page, batchSize);
            List<Book> batchBooks = getMinimalFieldBooks(userId, request, pageable);

            if (batchBooks.isEmpty()) {
                break;
            }

            streamedBooks.addAll(batchBooks);
            batchCount++;

            // メモリ効率化のため定期的にクリア
            if (batchCount % 10 == 0) {
                entityManager.clear();
                System.gc(); // ガベージコレクション促進
            }

            logger.debug("ストリーミングバッチ {}完了: 件数={}", batchCount, batchBooks.size());
            page++;
        }

        long processingTime = System.currentTimeMillis() - startTime;

        logger.debug("ストリーミング戦略実行完了: 総件数={}, バッチ数={}, 処理時間={}ms",
            streamedBooks.size(), batchCount, processingTime);

        return new OptimizedDataResult(
            streamedBooks,
            streamedBooks.size(),
            OptimizationStrategy.STREAMING,
            processingTime,
            batchCount
        );
    }

    /**
     * フィルタ付き書籍データ取得
     */
    private List<Book> getFilteredBooks(Long userId, ReportRequest request, Pageable pageable) {
        if (pageable != null) {
            return getFilteredBooksPage(userId, request, pageable).getContent();
        } else {
            return bookRepository.findByUserIdWithAuthors(userId);
        }
    }

    /**
     * ページング対応フィルタ付き書籍データ取得
     */
    private Page<Book> getFilteredBooksPage(Long userId, ReportRequest request, Pageable pageable) {
        // フィルター条件構築（簡易実装）
        Specification<Book> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);

        // ソート設定
        Sort sort = Sort.by(Sort.Direction.ASC, "id"); // IDでソートして一貫性確保
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        return bookRepository.findAll(spec, sortedPageable);
    }

    /**
     * 最小限フィールドのみの書籍データ取得
     */
    private List<Book> getMinimalFieldBooks(Long userId, ReportRequest request, Pageable pageable) {
        // プロジェクションクエリで必要フィールドのみ取得
        String jpql = "SELECT NEW com.library.management.entity.Book(b.id, b.title, b.publisher, b.readStatus) " +
                     "FROM Book b WHERE b.userId = :userId ORDER BY b.id";

        Query query = entityManager.createQuery(jpql);
        query.setParameter("userId", userId);
        query.setFirstResult(pageable.getPageNumber() * pageable.getPageSize());
        query.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Book> books = query.getResultList();
        return books;
    }

    /**
     * カウントクエリ構築
     */
    private String buildCountQuery(Long userId, ReportRequest request) {
        StringBuilder query = new StringBuilder("SELECT COUNT(b) FROM Book b WHERE b.userId = :userId");

        // フィルター条件追加（簡易実装）
        if (request.getFilters() != null) {
            // 実際のフィルター条件に応じて動的にクエリ構築
        }

        return query.toString();
    }

    /**
     * クエリパラメータ設定
     */
    private void setQueryParameters(Query query, Long userId, ReportRequest request) {
        query.setParameter("userId", userId);

        // フィルターパラメータ設定（簡易実装）
        if (request.getFilters() != null) {
            // 実際のフィルター条件に応じてパラメータ設定
        }
    }

    /**
     * リソース解放
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("ExecutorService をシャットダウンしました");
        }
    }

    /**
     * 最適化戦略列挙型
     */
    public enum OptimizationStrategy {
        IN_MEMORY("インメモリ"),
        BATCH_PROCESSING("バッチ処理"),
        PARALLEL_BATCH("並列バッチ"),
        STREAMING("ストリーミング");

        private final String description;

        OptimizationStrategy(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 最適化データ結果クラス
     */
    public static class OptimizedDataResult {
        private final List<Book> data;
        private final int totalRecords;
        private final OptimizationStrategy strategy;
        private final long processingTimeMs;
        private final int batchCount;

        public OptimizedDataResult(List<Book> data, int totalRecords, OptimizationStrategy strategy,
                                  long processingTimeMs, int batchCount) {
            this.data = data;
            this.totalRecords = totalRecords;
            this.strategy = strategy;
            this.processingTimeMs = processingTimeMs;
            this.batchCount = batchCount;
        }

        // Getters
        public List<Book> getData() { return data; }
        public int getTotalRecords() { return totalRecords; }
        public OptimizationStrategy getStrategy() { return strategy; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public int getBatchCount() { return batchCount; }

        public double getProcessingTimeSeconds() {
            return processingTimeMs / 1000.0;
        }

        public int getRecordsPerSecond() {
            return processingTimeMs > 0 ? (int) (totalRecords * 1000.0 / processingTimeMs) : 0;
        }
    }
}