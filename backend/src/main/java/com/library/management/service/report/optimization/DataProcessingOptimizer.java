package com.library.management.service.report.optimization;

import com.library.management.dto.ReportRequest;
import com.library.management.entity.Book;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * データ処理最適化サービス
 * 大量データの変換・集計処理を効率化
 */
@Service
public class DataProcessingOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(DataProcessingOptimizer.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.report.processing.parallel-threshold:1000}")
    private int parallelProcessingThreshold;

    @Value("${app.report.processing.thread-pool-size:4}")
    private int threadPoolSize;

    @Value("${app.report.processing.chunk-size:500}")
    private int chunkSize;

    private ExecutorService processingExecutor;

    @PostConstruct
    public void init() {
        this.processingExecutor = Executors.newFixedThreadPool(threadPoolSize);
    }

    /**
     * 最適化データ変換
     */
    public <T, R> List<R> optimizedTransform(List<T> sourceData, Function<T, R> transformer,
                                           ProcessingStrategy strategy) {
        long startTime = System.currentTimeMillis();

        try {
            logger.debug("最適化データ変換開始: データ数={}, 戦略={}", sourceData.size(), strategy);

            List<R> result;

            switch (strategy) {
                case SEQUENTIAL:
                    result = transformSequential(sourceData, transformer);
                    break;
                case PARALLEL_STREAM:
                    result = transformParallelStream(sourceData, transformer);
                    break;
                case CHUNKED_PARALLEL:
                    result = transformChunkedParallel(sourceData, transformer);
                    break;
                case ASYNC_CHUNKED:
                    result = transformAsyncChunked(sourceData, transformer);
                    break;
                default:
                    result = determineOptimalTransform(sourceData, transformer);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            logger.debug("最適化データ変換完了: 入力={}, 出力={}, 時間={}ms",
                sourceData.size(), result.size(), processingTime);

            return result;

        } catch (Exception e) {
            logger.error("データ変換エラー", e);
            throw new RuntimeException("データ変換に失敗しました", e);
        }
    }

    /**
     * 最適戦略自動決定変換
     */
    private <T, R> List<R> determineOptimalTransform(List<T> sourceData, Function<T, R> transformer) {
        if (sourceData.size() < parallelProcessingThreshold) {
            return transformSequential(sourceData, transformer);
        } else if (sourceData.size() < parallelProcessingThreshold * 5) {
            return transformParallelStream(sourceData, transformer);
        } else {
            return transformChunkedParallel(sourceData, transformer);
        }
    }

    /**
     * 順次変換
     */
    private <T, R> List<R> transformSequential(List<T> sourceData, Function<T, R> transformer) {
        return sourceData.stream()
            .map(transformer)
            .collect(Collectors.toList());
    }

    /**
     * 並列ストリーム変換
     */
    private <T, R> List<R> transformParallelStream(List<T> sourceData, Function<T, R> transformer) {
        return sourceData.parallelStream()
            .map(transformer)
            .collect(Collectors.toList());
    }

    /**
     * チャンク分割並列変換
     */
    private <T, R> List<R> transformChunkedParallel(List<T> sourceData, Function<T, R> transformer) {
        List<List<T>> chunks = partitionList(sourceData, chunkSize);
        List<Future<List<R>>> futures = new ArrayList<>();

        for (List<T> chunk : chunks) {
            Future<List<R>> future = processingExecutor.submit(() ->
                chunk.stream().map(transformer).collect(Collectors.toList())
            );
            futures.add(future);
        }

        List<R> result = new ArrayList<>();
        for (Future<List<R>> future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                logger.error("チャンク処理エラー", e);
                throw new RuntimeException(e);
            }
        }

        return result;
    }

    /**
     * 非同期チャンク変換
     */
    private <T, R> List<R> transformAsyncChunked(List<T> sourceData, Function<T, R> transformer) {
        List<List<T>> chunks = partitionList(sourceData, chunkSize);
        List<CompletableFuture<List<R>>> futures = new ArrayList<>();

        for (List<T> chunk : chunks) {
            CompletableFuture<List<R>> future = CompletableFuture.supplyAsync(() ->
                chunk.stream().map(transformer).collect(Collectors.toList()),
                processingExecutor
            );
            futures.add(future);
        }

        CompletableFuture<List<R>> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        ).thenApply(v -> futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList())
        );

        try {
            return allFutures.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("非同期処理エラー", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 最適化集計処理
     */
    public <T, K> Map<K, Long> optimizedGroupCount(List<T> data, Function<T, K> keyExtractor) {
        if (data.size() < parallelProcessingThreshold) {
            return data.stream()
                .collect(Collectors.groupingBy(keyExtractor, Collectors.counting()));
        } else {
            return data.parallelStream()
                .collect(Collectors.groupingBy(keyExtractor, Collectors.counting()));
        }
    }

    /**
     * 書籍データの最適化変換
     */
    public List<Map<String, Object>> optimizeBookDataForReport(List<Book> books, ReportRequest request) {
        logger.debug("書籍データ最適化変換開始: 件数={}", books.size());

        Function<Book, Map<String, Object>> transformer = book -> {
            Map<String, Object> bookData = new HashMap<>();
            bookData.put("id", book.getId());
            bookData.put("title", book.getTitle());
            bookData.put("publisher", book.getPublisher());
            bookData.put("readStatus", book.getReadStatus() != null ? book.getReadStatus().getName() : "未設定");

            // 著者情報の最適化
            if (book.getBookAuthors() != null && !book.getBookAuthors().isEmpty()) {
                String authors = book.getBookAuthors().stream()
                    .map(ba -> ba.getAuthor().getName())
                    .collect(Collectors.joining(", "));
                bookData.put("authors", authors);
            } else {
                bookData.put("authors", "");
            }

            // 作成日時
            bookData.put("createdAt", book.getCreatedAt());

            return bookData;
        };

        // データ量に応じた最適化戦略選択
        ProcessingStrategy strategy = chooseProcessingStrategy(books.size());

        return optimizedTransform(books, transformer, strategy);
    }

    /**
     * 統計データの最適化集計
     */
    public StatisticsResult optimizeStatisticsCalculation(List<Book> books) {
        logger.debug("統計データ最適化集計開始: 件数={}", books.size());

        long startTime = System.currentTimeMillis();

        StatisticsResult result = new StatisticsResult();

        if (books.size() < parallelProcessingThreshold) {
            // 順次処理
            result.setTotalCount(books.size());
            result.setStatusCounts(books.stream()
                .filter(book -> book.getReadStatus() != null)
                .collect(Collectors.groupingBy(
                    book -> book.getReadStatus().getName(),
                    Collectors.counting())));

            result.setPublisherCounts(books.stream()
                .filter(book -> book.getPublisher() != null && !book.getPublisher().trim().isEmpty())
                .collect(Collectors.groupingBy(
                    Book::getPublisher,
                    Collectors.counting())));

        } else {
            // 並列処理
            result.setTotalCount(books.size());
            result.setStatusCounts(books.parallelStream()
                .filter(book -> book.getReadStatus() != null)
                .collect(Collectors.groupingByConcurrent(
                    book -> book.getReadStatus().getName(),
                    Collectors.counting())));

            result.setPublisherCounts(books.parallelStream()
                .filter(book -> book.getPublisher() != null && !book.getPublisher().trim().isEmpty())
                .collect(Collectors.groupingByConcurrent(
                    Book::getPublisher,
                    Collectors.counting())));
        }

        long processingTime = System.currentTimeMillis() - startTime;
        result.setProcessingTimeMs(processingTime);

        logger.debug("統計データ最適化集計完了: 時間={}ms", processingTime);

        return result;
    }

    /**
     * メモリ効率的なデータフィルタリング
     */
    public <T> List<T> optimizedFilter(List<T> data, Function<T, Boolean> predicate) {
        if (data.size() < parallelProcessingThreshold) {
            return data.stream()
                .filter(predicate::apply)
                .collect(Collectors.toList());
        } else {
            return data.parallelStream()
                .filter(predicate::apply)
                .collect(Collectors.toList());
        }
    }

    /**
     * 大量データソート最適化
     */
    public <T> List<T> optimizedSort(List<T> data, Comparator<T> comparator) {
        if (data.size() < parallelProcessingThreshold) {
            return data.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
        } else {
            // 大量データの場合は外部ソートアルゴリズムを使用
            return performExternalSort(data, comparator);
        }
    }

    /**
     * 外部ソート実行
     */
    private <T> List<T> performExternalSort(List<T> data, Comparator<T> comparator) {
        // チャンクに分割してソート
        List<List<T>> chunks = partitionList(data, chunkSize);
        List<List<T>> sortedChunks = chunks.parallelStream()
            .map(chunk -> chunk.stream().sorted(comparator).collect(Collectors.toList()))
            .collect(Collectors.toList());

        // マージソート
        return mergeSortedChunks(sortedChunks, comparator);
    }

    /**
     * ソート済みチャンクのマージ
     */
    private <T> List<T> mergeSortedChunks(List<List<T>> sortedChunks, Comparator<T> comparator) {
        if (sortedChunks.isEmpty()) {
            return new ArrayList<>();
        }

        if (sortedChunks.size() == 1) {
            return new ArrayList<>(sortedChunks.get(0));
        }

        // 優先度キューを使用したマージ
        PriorityQueue<ChunkIterator<T>> pq = new PriorityQueue<>(
            Comparator.comparing(ChunkIterator::current, comparator)
        );

        for (List<T> chunk : sortedChunks) {
            if (!chunk.isEmpty()) {
                pq.offer(new ChunkIterator<>(chunk));
            }
        }

        List<T> result = new ArrayList<>();
        while (!pq.isEmpty()) {
            ChunkIterator<T> iterator = pq.poll();
            result.add(iterator.current());

            if (iterator.hasNext()) {
                iterator.next();
                pq.offer(iterator);
            }
        }

        return result;
    }

    /**
     * リスト分割ユーティリティ
     */
    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

    /**
     * 処理戦略選択
     */
    private ProcessingStrategy chooseProcessingStrategy(int dataSize) {
        if (dataSize < parallelProcessingThreshold) {
            return ProcessingStrategy.SEQUENTIAL;
        } else if (dataSize < parallelProcessingThreshold * 5) {
            return ProcessingStrategy.PARALLEL_STREAM;
        } else if (dataSize < parallelProcessingThreshold * 20) {
            return ProcessingStrategy.CHUNKED_PARALLEL;
        } else {
            return ProcessingStrategy.ASYNC_CHUNKED;
        }
    }

    /**
     * リソース解放
     */
    public void shutdown() {
        if (processingExecutor != null && !processingExecutor.isShutdown()) {
            processingExecutor.shutdown();
            try {
                if (!processingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    processingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                processingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 処理戦略列挙型
     */
    public enum ProcessingStrategy {
        SEQUENTIAL,
        PARALLEL_STREAM,
        CHUNKED_PARALLEL,
        ASYNC_CHUNKED,
        AUTO
    }

    /**
     * 統計結果クラス
     */
    public static class StatisticsResult {
        private int totalCount;
        private Map<String, Long> statusCounts = new HashMap<>();
        private Map<String, Long> publisherCounts = new HashMap<>();
        private long processingTimeMs;

        // Getters and Setters
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

        public Map<String, Long> getStatusCounts() { return statusCounts; }
        public void setStatusCounts(Map<String, Long> statusCounts) { this.statusCounts = statusCounts; }

        public Map<String, Long> getPublisherCounts() { return publisherCounts; }
        public void setPublisherCounts(Map<String, Long> publisherCounts) { this.publisherCounts = publisherCounts; }

        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    }

    /**
     * チャンクイテレータクラス
     */
    private static class ChunkIterator<T> {
        private final List<T> chunk;
        private int index = 0;

        public ChunkIterator(List<T> chunk) {
            this.chunk = chunk;
        }

        public T current() {
            return chunk.get(index);
        }

        public boolean hasNext() {
            return index + 1 < chunk.size();
        }

        public void next() {
            index++;
        }
    }
}