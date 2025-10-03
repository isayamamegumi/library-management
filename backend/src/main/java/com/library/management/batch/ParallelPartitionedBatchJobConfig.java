package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.library.management.dto.UserStats;
import com.library.management.entity.User;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.*;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Configuration
public class ParallelPartitionedBatchJobConfig {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BatchJobExecutionListener batchJobExecutionListener;

    // 並列パーティション処理ジョブ
    @Bean
    public Job parallelPartitionedJob(JobRepository jobRepository,
                                     Step masterStep,
                                     Step dataTransformationStep) {
        return new JobBuilder("parallelPartitionedJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(dataTransformationStep)  // データ変換ステップ
                .next(masterStep)              // 並列パーティションステップ
                .listener(batchJobExecutionListener)
                .build();
    }
    
    // データ変換・加工処理ステップ
    @Bean
    public Step dataTransformationStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      ItemReader<Map<String, Object>> rawDataReader,
                                      ItemProcessor<Map<String, Object>, Map<String, Object>> dataTransformer,
                                      ItemWriter<Map<String, Object>> transformedDataWriter) {
        return new StepBuilder("dataTransformationStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(100, transactionManager)
                .reader(rawDataReader)
                .processor(dataTransformer)
                .writer(transformedDataWriter)
                .build();
    }
    
    // 生データリーダー
    @Bean
    public ItemReader<Map<String, Object>> rawDataReader() {
        JdbcPagingItemReader<Map<String, Object>> reader = new JdbcPagingItemReader<>();
        reader.setDataSource(dataSource);
        reader.setPageSize(100);
        
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause(
            "b.id, b.title, b.publisher, b.isbn, b.published_date, b.created_at, " +
            "u.username, u.email, rs.name as read_status, g.name as genre_name"
        );
        queryProvider.setFromClause(
            "books b " +
            "JOIN users u ON b.user_id = u.id " +
            "LEFT JOIN read_statuses rs ON b.read_status_id = rs.id " +
            "LEFT JOIN genres g ON b.genre_id = g.id"
        );
        
        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put("id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        reader.setQueryProvider(queryProvider);
        reader.setRowMapper((rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getLong("id"));
            map.put("title", rs.getString("title"));
            map.put("publisher", rs.getString("publisher"));
            map.put("isbn", rs.getString("isbn"));
            map.put("published_date", rs.getDate("published_date"));
            map.put("created_at", rs.getTimestamp("created_at"));
            map.put("username", rs.getString("username"));
            map.put("email", rs.getString("email"));
            map.put("read_status", rs.getString("read_status"));
            map.put("genre_name", rs.getString("genre_name"));
            return map;
        });
        return reader;
    }
    
    // データ変換プロセッサー（データクレンジング、正規化、導出項目作成）
    @Bean
    public ItemProcessor<Map<String, Object>, Map<String, Object>> dataTransformer() {
        return item -> {
            Map<String, Object> transformed = new HashMap<>(item);
            
            // 1. データクレンジング
            String title = (String) item.get("title");
            if (title != null) {
                // タイトルの正規化（前後のスペースを削除、連続スペースを統合）
                String normalizedTitle = title.trim().replaceAll("\\s+", " ");
                transformed.put("normalized_title", normalizedTitle);
                transformed.put("title_length", normalizedTitle.length());
                transformed.put("title_word_count", normalizedTitle.split("\\s+").length);
            }
            
            // 2. ISBN正規化と検証
            String isbn = (String) item.get("isbn");
            if (isbn != null) {
                String cleanedIsbn = isbn.replaceAll("[^0-9X]", "");
                transformed.put("cleaned_isbn", cleanedIsbn);
                transformed.put("isbn_valid", isValidISBN(cleanedIsbn));
            }
            
            // 3. 登録からの経過日数計算
            java.sql.Timestamp createdAt = (java.sql.Timestamp) item.get("created_at");
            if (createdAt != null) {
                long diffMs = System.currentTimeMillis() - createdAt.getTime();
                long diffDays = diffMs / (24 * 60 * 60 * 1000);
                transformed.put("days_since_created", diffDays);

                // 登録からの経過日数カテゴリ
                String ageCategory;
                if (diffDays <= 7) {
                    ageCategory = "NEW";
                } else if (diffDays <= 30) {
                    ageCategory = "RECENT";
                } else if (diffDays <= 365) {
                    ageCategory = "MODERATE";
                } else {
                    ageCategory = "OLD";
                }
                transformed.put("book_age_category", ageCategory);
            }
            
            // 4. ユーザーデータの正規化
            String email = (String) item.get("email");
            if (email != null) {
                transformed.put("email_domain", email.substring(email.indexOf("@") + 1));
                transformed.put("email_valid", email.matches("^[A-Za-z0-9+_.-]+@(.+)$"));
            }
            
            // 5. ジャンルカテゴリ分類
            String genreName = (String) item.get("genre_name");
            if (genreName != null) {
                String genreCategory = categorizeGenre(genreName);
                transformed.put("genre_category", genreCategory);
            }
            
            // 6. メタデータ追加
            transformed.put("processing_timestamp", LocalDateTime.now());
            transformed.put("data_source", "BATCH_TRANSFORMATION");
            
            return transformed;
        };
    }
    
    // 変換データライター（一時テーブルやキャッシュに保存）
    @Bean
    public ItemWriter<Map<String, Object>> transformedDataWriter() {
        return items -> {
            List<Map<String, Object>> itemList = new ArrayList<>();
            items.forEach(itemList::add);
            
            // 変換結果をJSONで保存
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String transformedJson = mapper.writeValueAsString(itemList);
            
            // batch_statisticsテーブルに保存
            jdbcTemplate.update(
                "INSERT INTO batch_statistics (report_type, target_date, data_json) VALUES (?, ?, ?::jsonb) " +
                "ON CONFLICT (report_type, target_date) DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
                "TRANSFORMED_DATA", LocalDate.now(), transformedJson, transformedJson);
            
            System.out.println("データ変換完了: " + itemList.size() + "件");
        };
    }
    
    // 並列処理のMaster Step
    @Bean
    public Step masterStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          Partitioner userPartitioner,
                          Step partitionedWorkerStep,
                          @Qualifier("partitionTaskExecutor") TaskExecutor partitionTaskExecutor) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner("partitionedWorkerStep", userPartitioner)
                .step(partitionedWorkerStep)
                .gridSize(4)  // 4つのパーティションで並列処理
                .taskExecutor(partitionTaskExecutor)
                .build();
    }
    
    // ユーザーID範囲でパーティション分割
    @Bean
    public Partitioner userPartitioner() {
        return gridSize -> {
            Map<String, ExecutionContext> partitions = new HashMap<>();
            
            try {
                // ユーザーIDの範囲を取得
                Integer minUserId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MIN(id), 1) FROM users", Integer.class);
                Integer maxUserId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(id), 1) FROM users", Integer.class);
                
                int range = Math.max(1, (maxUserId - minUserId + 1) / gridSize);
                
                System.out.println("パーティション設定: minUserId=" + minUserId + ", maxUserId=" + maxUserId + ", gridSize=" + gridSize);
                
                for (int i = 0; i < gridSize; i++) {
                    ExecutionContext context = new ExecutionContext();
                    int startId = minUserId + (i * range);
                    int endId = (i == gridSize - 1) ? maxUserId : startId + range - 1;
                    
                    context.put("startUserId", startId);
                    context.put("endUserId", endId);
                    context.put("partitionNumber", i);
                    
                    partitions.put("partition" + i, context);
                    System.out.println("パーティション" + i + ": userId " + startId + " to " + endId);
                }
                
            } catch (Exception e) {
                System.err.println("パーティション作成エラー: " + e.getMessage());
                // フォールバック: 単一パーティション
                ExecutionContext fallbackContext = new ExecutionContext();
                fallbackContext.put("startUserId", 1);
                fallbackContext.put("endUserId", Integer.MAX_VALUE);
                fallbackContext.put("partitionNumber", 0);
                partitions.put("partition0", fallbackContext);
            }
            
            return partitions;
        };
    }
    
    // Worker Step（パーティション毎の処理）
    @Bean
    public Step partitionedWorkerStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          ItemReader<User> partitionedUserReader,
                          ItemProcessor<User, UserStats> optimizedUserProcessor,
                          ItemWriter<UserStats> partitionedUserWriter) {
        return new StepBuilder("partitionedWorkerStep", jobRepository)
                .<User, UserStats>chunk(50, transactionManager)  // メモリ最適化されたチャンクサイズ
                .reader(partitionedUserReader)
                .processor(optimizedUserProcessor)
                .writer(partitionedUserWriter)
                .faultTolerant()
                .skipLimit(100)  // 並列処理対応のため上限を増やす
                .skip(Exception.class)
                .noRetry(Exception.class)  // リトライを無効化してキャッシュキー競合を回避
                .build();
    }
    
    // パーティション範囲内のユーザーデータリーダー
    @Bean
    @StepScope
    public ItemReader<User> partitionedUserReader(@Value("#{stepExecutionContext[startUserId]}") Integer startUserId,
                                                  @Value("#{stepExecutionContext[endUserId]}") Integer endUserId,
                                                  @Value("#{stepExecutionContext[partitionNumber]}") Integer partitionNumber) {
        System.out.println("パーティション" + partitionNumber + "用リーダー開始: userId " + startUserId + " to " + endUserId);

        JdbcPagingItemReader<User> reader = new JdbcPagingItemReader<>();
        reader.setDataSource(dataSource);
        reader.setPageSize(50);

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("id, username, email, created_at");
        queryProvider.setFromClause("users");
        queryProvider.setWhereClause("id BETWEEN " + startUserId + " AND " + endUserId);

        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put("id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        reader.setQueryProvider(queryProvider);
        reader.setRowMapper((rs, rowNum) -> {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            java.sql.Timestamp timestamp = rs.getTimestamp("created_at");
            if (timestamp != null) {
                user.setCreatedAt(timestamp.toLocalDateTime());
            }
            return user;
        });
        return reader;
    }
    
    // メモリ最適化されたユーザー統計プロセッサー
    @Bean
    public ItemProcessor<User, UserStats> optimizedUserProcessor() {
        return user -> {
            try {
                UserStats stats = new UserStats();
                stats.setUserId(user.getId());
                stats.setUsername(user.getUsername());
                
                // メモリ効率的なクエリ（必要なデータのみ取得）
                Map<String, Object> bookStats = jdbcTemplate.queryForMap("""
                    SELECT 
                        COUNT(*) as total_books,
                        COUNT(CASE WHEN rs.name = '読了' THEN 1 END) as completed_books,
                        COUNT(CASE WHEN rs.name = '読書中' THEN 1 END) as reading_books,
                        COALESCE(MAX(g.name), 'N/A') as favorite_genre
                    FROM books b
                    LEFT JOIN read_statuses rs ON b.read_status_id = rs.id
                    LEFT JOIN genres g ON b.genre_id = g.id
                    WHERE b.user_id = ?
                    """, user.getId());
                
                stats.setTotalBooks(((Number) bookStats.get("total_books")).intValue());
                stats.setCompletedBooks(((Number) bookStats.get("completed_books")).intValue());
                stats.setReadingBooks(((Number) bookStats.get("reading_books")).intValue());
                stats.setFavoriteGenre((String) bookStats.get("favorite_genre"));
                
                // 読了率計算
                int totalBooks = stats.getTotalBooks();
                if (totalBooks > 0) {
                    double progressRate = (stats.getCompletedBooks().doubleValue() / totalBooks) * 100;
                    stats.setProgressRate(Math.round(progressRate * 100.0) / 100.0);
                } else {
                    stats.setProgressRate(0.0);
                }
                
                stats.setTargetMonth(LocalDate.now());
                return stats;
                
            } catch (Exception e) {
                System.err.println("ユーザー統計処理エラー (userId=" + user.getId() + "): " + e.getMessage());
                throw e; // リトライ・スキップのため再スロー
            }
        };
    }
    
    // パーティション結果ライター（スレッドセーフ）
    @Bean
    public ItemWriter<UserStats> partitionedUserWriter() {
        return items -> {
            if (items == null || !items.iterator().hasNext()) {
                return;  // 空の場合は何もしない
            }

            List<UserStats> statsList = new ArrayList<>();
            items.forEach(statsList::add);
            String currentThread = Thread.currentThread().getName();
            long timestamp = System.currentTimeMillis();

            synchronized (ParallelPartitionedBatchJobConfig.class) {  // クラスレベルのロック
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new JavaTimeModule());

                    // パーティション別の結果として保存（ユニークキーに時刻を含める）
                    String partitionKey = "PARTITION_RESULT_" + currentThread + "_" + timestamp;
                    String statsJson = mapper.writeValueAsString(statsList);

                    jdbcTemplate.update(
                        "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
                        "VALUES (?, ?, ?::jsonb)",
                        partitionKey, LocalDate.now(), statsJson);

                    System.out.println("[パーティション結果] スレッド: " + currentThread + ", 件数: " + statsList.size());

                } catch (Exception e) {
                    System.err.println("パーティション結果書き込みエラー (スレッド: " + currentThread + "): " + e.getMessage());
                    e.printStackTrace();
                    // エラーをスローせずログに記録のみ（skipLimit内で処理継続）
                }
            }
        };
    }
    
    // ユーティリティメソッド
    private boolean isValidISBN(String isbn) {
        if (isbn == null || (isbn.length() != 10 && isbn.length() != 13)) {
            return false;
        }
        
        try {
            if (isbn.length() == 10) {
                // ISBN-10検証
                int sum = 0;
                for (int i = 0; i < 9; i++) {
                    sum += Character.getNumericValue(isbn.charAt(i)) * (10 - i);
                }
                char checkChar = isbn.charAt(9);
                int checkDigit = (checkChar == 'X') ? 10 : Character.getNumericValue(checkChar);
                return (sum + checkDigit) % 11 == 0;
            } else {
                // ISBN-13検証
                int sum = 0;
                for (int i = 0; i < 12; i++) {
                    int digit = Character.getNumericValue(isbn.charAt(i));
                    sum += digit * ((i % 2 == 0) ? 1 : 3);
                }
                int checkDigit = Character.getNumericValue(isbn.charAt(12));
                return (sum + checkDigit) % 10 == 0;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    private String categorizeGenre(String genreName) {
        if (genreName == null) return "UNKNOWN";
        
        String lowerGenre = genreName.toLowerCase();
        if (lowerGenre.contains("小説") || lowerGenre.contains("フィクション")) {
            return "FICTION";
        } else if (lowerGenre.contains("技術") || lowerGenre.contains("コンピュータ")) {
            return "TECHNICAL";
        } else if (lowerGenre.contains("ビジネス") || lowerGenre.contains("経済")) {
            return "BUSINESS";
        } else if (lowerGenre.contains("歴史") || lowerGenre.contains("伝記")) {
            return "HISTORY";
        } else {
            return "OTHER";
        }
    }
}