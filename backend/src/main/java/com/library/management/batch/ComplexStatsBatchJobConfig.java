package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.library.management.dto.UserStats;
import com.library.management.entity.User;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.*;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Configuration
public class ComplexStatsBatchJobConfig {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // 複数テーブル結合での集計処理ジョブ
    @Bean
    public Job complexStatsJob(JobRepository jobRepository, 
                              Step complexUserAnalysisStep, 
                              Step complexGenreAnalysisStep,
                              Step complexReadingPaceAnalysisStep) {
        return new JobBuilder("complexStatsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(complexUserAnalysisStep)
                .next(complexGenreAnalysisStep)
                .next(complexReadingPaceAnalysisStep)
                .listener(new JobExecutionListener() {
                    @Override
                    public void beforeJob(JobExecution jobExecution) {
                        System.out.println("複集計ジョブ開始: " + LocalDateTime.now());
                    }
                    
                    @Override
                    public void afterJob(JobExecution jobExecution) {
                        System.out.println("複集計ジョブ完了: " + jobExecution.getStatus() + " at " + LocalDateTime.now());
                    }
                })
                .build();
    }
    
    // ユーザー別詳細統計Step
    @Bean
    public Step complexUserAnalysisStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                ItemReader<Map<String, Object>> complexUserReader,
                                ItemProcessor<Map<String, Object>, UserStats> complexUserProcessor,
                                ItemWriter<UserStats> complexUserWriter) {
        return new StepBuilder("complexUserAnalysisStep", jobRepository)
                .<Map<String, Object>, UserStats>chunk(50, transactionManager)
                .reader(complexUserReader)
                .processor(complexUserProcessor)
                .writer(complexUserWriter)
                .build();
    }
    
    // 複集ユーザーデータリーダー（複数テーブル結合）
    @Bean
    @StepScope
    public ItemReader<Map<String, Object>> complexUserReader(@Value("#{jobParameters[targetDate]}") String targetDate) {
        JdbcPagingItemReader<Map<String, Object>> reader = new JdbcPagingItemReader<>();
        reader.setDataSource(dataSource);
        reader.setPageSize(50);
        
        // 複集SQLクエリ（複数テーブル結合）
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause(
            "u.id as user_id, u.username, " +
            "COUNT(b.id) as total_books, " +
            "COUNT(CASE WHEN rs.name = '読了' THEN 1 END) as completed_books, " +
            "COUNT(CASE WHEN rs.name = '読書中' THEN 1 END) as reading_books, " +
            "COUNT(CASE WHEN rs.name = '未読' THEN 1 END) as unread_books, " +
            "COALESCE(MAX(g.name), 'N/A') as favorite_genre, " +
            "AVG(EXTRACT(EPOCH FROM (b.updated_at - b.created_at))/86400) as avg_reading_days, " +
            "COUNT(CASE WHEN b.created_at >= CURRENT_DATE - INTERVAL '30 days' THEN 1 END) as books_last_30_days, " +
            "COUNT(CASE WHEN b.created_at >= CURRENT_DATE - INTERVAL '365 days' THEN 1 END) as books_last_year"
        );
        queryProvider.setFromClause(
            "users u " +
            "LEFT JOIN books b ON u.id = b.user_id " +
            "LEFT JOIN read_statuses rs ON b.read_status_id = rs.id " +
            "LEFT JOIN genres g ON b.genre_id = g.id"
        );
        queryProvider.setWhereClause("u.id IS NOT NULL");
        queryProvider.setGroupClause("u.id, u.username");
        
        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put("user_id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);
        
        reader.setQueryProvider(queryProvider);
        return reader;
    }
    
    // 複集ユーザーデータプロセッサー
    @Bean
    public ItemProcessor<Map<String, Object>, UserStats> complexUserProcessor() {
        return item -> {
            UserStats stats = new UserStats();
            stats.setUserId(((Number) item.get("user_id")).longValue());
            stats.setUsername((String) item.get("username"));
            stats.setTotalBooks(((Number) item.get("total_books")).intValue());
            stats.setCompletedBooks(((Number) item.get("completed_books")).intValue());
            stats.setReadingBooks(((Number) item.get("reading_books")).intValue());
            stats.setFavoriteGenre((String) item.get("favorite_genre"));
            
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
        };
    }
    
    // 複集ユーザーデータライター
    @Bean
    public ItemWriter<UserStats> complexUserWriter() {
        return items -> {
            List<UserStats> statsList = new ArrayList<>();
            items.forEach(statsList::add);
            
            // JSON形式で保存
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String statsJson = mapper.writeValueAsString(statsList);
            
            // batch_statisticsテーブルに保存
            jdbcTemplate.update(
                "INSERT INTO batch_statistics (report_type, target_date, data_json) VALUES (?, ?, ?::jsonb) " +
                "ON CONFLICT (report_type, target_date) DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
                "USER_ANALYSIS", LocalDate.now(), statsJson, statsJson);
                
            System.out.println("ユーザー統計保存完了: " + statsList.size() + "件");
        };
    }
    
    // ジャンル別分析Step
    @Bean
    public Step complexGenreAnalysisStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager) {
        return new StepBuilder("complexGenreAnalysisStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    
                    // ジャンル別詳細統計
                    List<Map<String, Object>> genreStats = jdbcTemplate.queryForList("""
                        SELECT 
                            g.name as genre_name,
                            COUNT(b.id) as total_books,
                            COUNT(CASE WHEN rs.name = '読了' THEN 1 END) as completed_books,
                            COUNT(DISTINCT b.user_id) as unique_readers,
                            AVG(CASE WHEN rs.name = '読了' THEN EXTRACT(EPOCH FROM (b.updated_at - b.created_at))/86400 END) as avg_reading_days,
                            COUNT(CASE WHEN b.created_at >= CURRENT_DATE - INTERVAL '30 days' THEN 1 END) as books_last_30_days,
                            ROUND(
                                COUNT(CASE WHEN rs.name = '読了' THEN 1 END) * 100.0 / NULLIF(COUNT(b.id), 0), 2
                            ) as completion_rate
                        FROM genres g
                        LEFT JOIN books b ON g.id = b.genre_id
                        LEFT JOIN read_statuses rs ON b.read_status_id = rs.id
                        WHERE g.id IS NOT NULL
                        GROUP BY g.id, g.name
                        ORDER BY total_books DESC
                        """);
                    
                    // 人気ジャンルランキング
                    List<Map<String, Object>> popularGenres = jdbcTemplate.queryForList("""
                        SELECT 
                            g.name as genre_name,
                            COUNT(DISTINCT b.user_id) as reader_count,
                            COUNT(b.id) as book_count,
                            ROUND(AVG(CASE WHEN rs.name = '読了' THEN 1.0 ELSE 0.0 END) * 100, 2) as completion_rate
                        FROM genres g
                        LEFT JOIN books b ON g.id = b.genre_id
                        LEFT JOIN read_statuses rs ON b.read_status_id = rs.id
                        WHERE b.created_at >= CURRENT_DATE - INTERVAL '90 days'
                        GROUP BY g.id, g.name
                        HAVING COUNT(b.id) >= 3
                        ORDER BY reader_count DESC, completion_rate DESC
                        LIMIT 20
                        """);
                    
                    // シーズン分析（月別トレンド）
                    List<Map<String, Object>> seasonalTrends = jdbcTemplate.queryForList("""
                        SELECT 
                            g.name as genre_name,
                            EXTRACT(MONTH FROM b.created_at) as month,
                            COUNT(b.id) as book_count
                        FROM genres g
                        JOIN books b ON g.id = b.genre_id
                        WHERE b.created_at >= CURRENT_DATE - INTERVAL '12 months'
                        GROUP BY g.id, g.name, EXTRACT(MONTH FROM b.created_at)
                        ORDER BY g.name, month
                        """);
                    
                    Map<String, Object> genreAnalysisData = new HashMap<>();
                    genreAnalysisData.put("genreStats", genreStats);
                    genreAnalysisData.put("popularGenres", popularGenres);
                    genreAnalysisData.put("seasonalTrends", seasonalTrends);
                    genreAnalysisData.put("generatedAt", LocalDateTime.now());
                    
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new JavaTimeModule());
                    String analysisJson = mapper.writeValueAsString(genreAnalysisData);
                    
                    jdbcTemplate.update(
                        "INSERT INTO batch_statistics (report_type, target_date, data_json) VALUES (?, ?, ?::jsonb) " +
                        "ON CONFLICT (report_type, target_date) DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
                        "GENRE_ANALYSIS", LocalDate.now(), analysisJson, analysisJson);
                    
                    System.out.println("ジャンル分析完了");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
    
    // 読書ペース分析Step
    @Bean
    public Step complexReadingPaceAnalysisStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager) {
        return new StepBuilder("complexReadingPaceAnalysisStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    
                    // ユーザー別読書ペース分析
                    List<Map<String, Object>> readingPaceData = jdbcTemplate.queryForList("""
                        WITH user_reading_stats AS (
                            SELECT 
                                u.id as user_id,
                                u.username,
                                COUNT(CASE WHEN rs.name = '読了' THEN 1 END) as completed_count,
                                AVG(CASE WHEN rs.name = '読了' AND b.updated_at > b.created_at 
                                    THEN EXTRACT(EPOCH FROM (b.updated_at - b.created_at))/86400 END) as avg_reading_days,
                                COUNT(CASE WHEN b.created_at >= CURRENT_DATE - INTERVAL '30 days' THEN 1 END) as books_last_30_days,
                                COUNT(CASE WHEN b.created_at >= CURRENT_DATE - INTERVAL '7 days' THEN 1 END) as books_last_7_days
                            FROM users u
                            LEFT JOIN books b ON u.id = b.user_id
                            LEFT JOIN read_statuses rs ON b.read_status_id = rs.id
                            GROUP BY u.id, u.username
                        )
                        SELECT 
                            user_id,
                            username,
                            completed_count,
                            ROUND(COALESCE(avg_reading_days, 0), 2) as avg_reading_days,
                            books_last_30_days,
                            books_last_7_days,
                            CASE 
                                WHEN avg_reading_days <= 7 THEN 'Fast'
                                WHEN avg_reading_days <= 21 THEN 'Normal'
                                ELSE 'Slow'
                            END as reading_pace_category,
                            CASE 
                                WHEN books_last_7_days >= 2 THEN 'Very Active'
                                WHEN books_last_7_days = 1 THEN 'Active'
                                WHEN books_last_30_days >= 1 THEN 'Moderate'
                                ELSE 'Inactive'
                            END as activity_level
                        FROM user_reading_stats
                        WHERE completed_count > 0
                        ORDER BY completed_count DESC
                        """);
                    
                    // システム全体の読書統計
                    Map<String, Object> systemStats = jdbcTemplate.queryForMap("""
                        SELECT 
                            COUNT(DISTINCT u.id) as active_users,
                            COUNT(b.id) as total_books,
                            COUNT(CASE WHEN rs.name = '読了' THEN 1 END) as completed_books,
                            ROUND(AVG(CASE WHEN rs.name = '読了' AND b.updated_at > b.created_at 
                                THEN EXTRACT(EPOCH FROM (b.updated_at - b.created_at))/86400 END), 2) as system_avg_reading_days,
                            ROUND(COUNT(CASE WHEN rs.name = '読了' THEN 1 END) * 100.0 / NULLIF(COUNT(b.id), 0), 2) as system_completion_rate
                        FROM users u
                        LEFT JOIN books b ON u.id = b.user_id
                        LEFT JOIN read_statuses rs ON b.read_status_id = rs.id
                        WHERE b.id IS NOT NULL
                        """);
                    
                    Map<String, Object> readingPaceAnalysis = new HashMap<>();
                    readingPaceAnalysis.put("userReadingPace", readingPaceData);
                    readingPaceAnalysis.put("systemStats", systemStats);
                    readingPaceAnalysis.put("generatedAt", LocalDateTime.now());
                    
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new JavaTimeModule());
                    String analysisJson = mapper.writeValueAsString(readingPaceAnalysis);
                    
                    jdbcTemplate.update(
                        "INSERT INTO batch_statistics (report_type, target_date, data_json) VALUES (?, ?, ?::jsonb) " +
                        "ON CONFLICT (report_type, target_date) DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
                        "READING_PACE_ANALYSIS", LocalDate.now(), analysisJson, analysisJson);
                    
                    System.out.println("読書ペース分析完了");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}