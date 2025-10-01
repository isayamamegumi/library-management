package com.library.management.service.report.optimization;

import com.library.management.dto.ReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クエリ最適化サービス
 * データベースクエリの最適化とキャッシュ管理
 */
@Service
public class QueryOptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(QueryOptimizationService.class);

    @PersistenceContext
    private EntityManager entityManager;

    // クエリ実行統計のキャッシュ
    private final Map<String, QueryStatistics> queryStatsCache = new ConcurrentHashMap<>();

    /**
     * 最適化されたクエリ実行
     */
    public <T> List<T> executeOptimizedQuery(String baseQuery, Map<String, Object> parameters,
                                           Class<T> resultType, OptimizationHint hint) {
        long startTime = System.currentTimeMillis();

        try {
            // クエリ最適化
            String optimizedQuery = optimizeQuery(baseQuery, parameters, hint);

            // クエリ実行
            Query query = entityManager.createQuery(optimizedQuery, resultType);
            setQueryParameters(query, parameters);
            applyQueryHints(query, hint);

            @SuppressWarnings("unchecked")
            List<T> results = query.getResultList();

            // 統計記録
            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryStatistics(baseQuery, results.size(), executionTime);

            return results;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryError(baseQuery, executionTime, e);
            throw e;
        }
    }

    /**
     * ページング対応最適化クエリ実行
     */
    public <T> PagedQueryResult<T> executeOptimizedPagedQuery(String baseQuery, String countQuery,
                                                            Map<String, Object> parameters,
                                                            Class<T> resultType,
                                                            Pageable pageable,
                                                            OptimizationHint hint) {
        long startTime = System.currentTimeMillis();

        try {
            // カウントクエリ実行
            Query countQ = entityManager.createQuery(optimizeQuery(countQuery, parameters, hint));
            setQueryParameters(countQ, parameters);
            Long totalCount = (Long) countQ.getSingleResult();

            // データクエリ実行
            String optimizedQuery = optimizeQuery(baseQuery, parameters, hint);
            Query dataQuery = entityManager.createQuery(optimizedQuery, resultType);
            setQueryParameters(dataQuery, parameters);
            applyQueryHints(dataQuery, hint);

            // ページング適用
            dataQuery.setFirstResult((int) pageable.getOffset());
            dataQuery.setMaxResults(pageable.getPageSize());

            @SuppressWarnings("unchecked")
            List<T> results = dataQuery.getResultList();

            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryStatistics(baseQuery, results.size(), executionTime);

            return new PagedQueryResult<>(results, totalCount, pageable);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            recordQueryError(baseQuery, executionTime, e);
            throw e;
        }
    }

    /**
     * クエリ最適化
     */
    private String optimizeQuery(String baseQuery, Map<String, Object> parameters, OptimizationHint hint) {
        StringBuilder optimizedQuery = new StringBuilder(baseQuery);

        // インデックスヒント追加
        if (hint.useIndexHints) {
            addIndexHints(optimizedQuery, hint);
        }

        // フェッチジョイン最適化
        if (hint.optimizeFetchJoins) {
            optimizeFetchJoins(optimizedQuery);
        }

        // 不要なフィールド除外
        if (hint.selectOnlyRequiredFields) {
            optimizeSelectFields(optimizedQuery, hint.requiredFields);
        }

        logger.debug("クエリ最適化完了: 元={}, 最適化後={}", baseQuery.length(), optimizedQuery.length());

        return optimizedQuery.toString();
    }

    /**
     * インデックスヒント追加
     */
    private void addIndexHints(StringBuilder query, OptimizationHint hint) {
        if (hint.suggestedIndexes != null && !hint.suggestedIndexes.isEmpty()) {
            // MySQL等のインデックスヒント構文に対応
            for (String index : hint.suggestedIndexes) {
                String hintClause = " /*+ INDEX(" + index + ") */ ";
                int fromIndex = query.indexOf("FROM");
                if (fromIndex > 0) {
                    query.insert(fromIndex, hintClause);
                    break;
                }
            }
        }
    }

    /**
     * フェッチジョイン最適化
     */
    private void optimizeFetchJoins(StringBuilder query) {
        // N+1問題回避のためのフェッチジョイン最適化
        String queryStr = query.toString().toLowerCase();

        if (queryStr.contains("book") && !queryStr.contains("fetch")) {
            // 書籍エンティティの場合、関連エンティティをフェッチジョイン
            int fromIndex = query.indexOf("FROM");
            if (fromIndex > 0) {
                String joinClause = " LEFT JOIN FETCH b.bookAuthors ba LEFT JOIN FETCH ba.author ";
                int whereIndex = query.indexOf("WHERE");
                if (whereIndex > fromIndex) {
                    query.insert(whereIndex - 1, joinClause);
                } else {
                    query.append(joinClause);
                }
            }
        }
    }

    /**
     * SELECT句最適化
     */
    private void optimizeSelectFields(StringBuilder query, List<String> requiredFields) {
        if (requiredFields == null || requiredFields.isEmpty()) {
            return;
        }

        // SELECT句の最適化（必要なフィールドのみ選択）
        String selectClause = String.join(", ", requiredFields);
        int selectIndex = query.indexOf("SELECT");
        int fromIndex = query.indexOf("FROM");

        if (selectIndex >= 0 && fromIndex > selectIndex) {
            query.replace(selectIndex + 6, fromIndex - 1, " " + selectClause + " ");
        }
    }

    /**
     * クエリパラメータ設定
     */
    private void setQueryParameters(Query query, Map<String, Object> parameters) {
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * クエリヒント適用
     */
    private void applyQueryHints(Query query, OptimizationHint hint) {
        if (hint.queryTimeout > 0) {
            query.setHint("jakarta.persistence.query.timeout", hint.queryTimeout);
        }

        if (hint.readOnly) {
            query.setHint("org.hibernate.readOnly", true);
        }

        if (hint.fetchSize > 0) {
            query.setHint("org.hibernate.fetchSize", hint.fetchSize);
        }

        if (hint.cacheEnabled) {
            query.setHint("org.hibernate.cacheable", true);
        }
    }

    /**
     * クエリ統計記録
     */
    private void recordQueryStatistics(String query, int resultCount, long executionTime) {
        String queryKey = generateQueryKey(query);

        queryStatsCache.compute(queryKey, (key, existing) -> {
            if (existing == null) {
                return new QueryStatistics(query, 1, executionTime, resultCount, executionTime, executionTime);
            } else {
                return existing.update(executionTime, resultCount);
            }
        });
    }

    /**
     * クエリエラー記録
     */
    private void recordQueryError(String query, long executionTime, Exception error) {
        String queryKey = generateQueryKey(query);
        logger.warn("クエリ実行エラー: query={}, time={}ms, error={}", queryKey, executionTime, error.getMessage());

        queryStatsCache.compute(queryKey, (key, existing) -> {
            if (existing == null) {
                return new QueryStatistics(query, 0, 0, 0, 0, 0).incrementError();
            } else {
                return existing.incrementError();
            }
        });
    }

    /**
     * クエリキー生成
     */
    private String generateQueryKey(String query) {
        return query.replaceAll("\\s+", " ").trim().substring(0, Math.min(100, query.length()));
    }

    /**
     * クエリ統計取得
     */
    public Map<String, QueryStatistics> getQueryStatistics() {
        return new HashMap<>(queryStatsCache);
    }

    /**
     * 遅いクエリの特定
     */
    public List<QueryStatistics> getSlowQueries(int threshold) {
        return queryStatsCache.values().stream()
            .filter(stats -> stats.getAverageExecutionTime() > threshold)
            .sorted((a, b) -> Long.compare(b.getAverageExecutionTime(), a.getAverageExecutionTime()))
            .toList();
    }

    /**
     * レポート用最適化ヒント生成
     */
    public OptimizationHint createReportOptimizationHint(Long userId, ReportRequest request, int estimatedRecords) {
        OptimizationHint hint = new OptimizationHint();

        // データ量に応じた最適化
        if (estimatedRecords > 10000) {
            hint.readOnly = true;
            hint.fetchSize = 1000;
            hint.queryTimeout = 60000; // 60秒
            hint.optimizeFetchJoins = true;
        } else if (estimatedRecords > 1000) {
            hint.fetchSize = 500;
            hint.queryTimeout = 30000; // 30秒
        } else {
            hint.cacheEnabled = true;
            hint.queryTimeout = 10000; // 10秒
        }

        // レポートタイプに応じた最適化
        if ("BOOK_LIST".equals(request.getReportType())) {
            hint.suggestedIndexes = List.of("idx_books_user_id", "idx_books_created_at");
            hint.requiredFields = List.of("b.id", "b.title", "b.publisher", "b.readStatus");
            hint.selectOnlyRequiredFields = estimatedRecords > 5000;
        }

        return hint;
    }

    /**
     * 最適化ヒントクラス
     */
    public static class OptimizationHint {
        public boolean useIndexHints = false;
        public boolean optimizeFetchJoins = false;
        public boolean selectOnlyRequiredFields = false;
        public boolean readOnly = false;
        public boolean cacheEnabled = false;
        public int fetchSize = 0;
        public int queryTimeout = 0;
        public List<String> suggestedIndexes = new ArrayList<>();
        public List<String> requiredFields = new ArrayList<>();
    }

    /**
     * ページングクエリ結果クラス
     */
    public static class PagedQueryResult<T> {
        private final List<T> content;
        private final long totalCount;
        private final Pageable pageable;

        public PagedQueryResult(List<T> content, long totalCount, Pageable pageable) {
            this.content = content;
            this.totalCount = totalCount;
            this.pageable = pageable;
        }

        public List<T> getContent() { return content; }
        public long getTotalCount() { return totalCount; }
        public Pageable getPageable() { return pageable; }
        public boolean hasNext() { return (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalCount; }
    }

    /**
     * クエリ統計クラス
     */
    public static class QueryStatistics {
        private final String query;
        private int executionCount;
        private long totalExecutionTime;
        private int totalResultCount;
        private long minExecutionTime;
        private long maxExecutionTime;
        private int errorCount;

        public QueryStatistics(String query, int executionCount, long totalExecutionTime,
                             int totalResultCount, long minExecutionTime, long maxExecutionTime) {
            this.query = query;
            this.executionCount = executionCount;
            this.totalExecutionTime = totalExecutionTime;
            this.totalResultCount = totalResultCount;
            this.minExecutionTime = minExecutionTime;
            this.maxExecutionTime = maxExecutionTime;
            this.errorCount = 0;
        }

        public QueryStatistics update(long executionTime, int resultCount) {
            return new QueryStatistics(
                query,
                executionCount + 1,
                totalExecutionTime + executionTime,
                totalResultCount + resultCount,
                Math.min(minExecutionTime, executionTime),
                Math.max(maxExecutionTime, executionTime)
            );
        }

        public QueryStatistics incrementError() {
            QueryStatistics updated = new QueryStatistics(query, executionCount, totalExecutionTime,
                totalResultCount, minExecutionTime, maxExecutionTime);
            updated.errorCount = this.errorCount + 1;
            return updated;
        }

        // Getters
        public String getQuery() { return query; }
        public int getExecutionCount() { return executionCount; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public long getAverageExecutionTime() {
            return executionCount > 0 ? totalExecutionTime / executionCount : 0;
        }
        public double getAverageResultCount() {
            return executionCount > 0 ? (double) totalResultCount / executionCount : 0;
        }
        public long getMinExecutionTime() { return minExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime; }
        public int getErrorCount() { return errorCount; }
    }
}