package com.library.management.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.management.dto.BookRanking;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PopularityRankingTasklet implements Tasklet {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public RepeatStatus execute(StepContribution contribution, 
                               ChunkContext chunkContext) throws Exception {
        
        // 過去30日間のデータを対象
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        
        // 人気度ランキング生成（登録数 + 読了数の重み付けスコア）
        List<Map<String, Object>> popularityData = jdbcTemplate.queryForList(
            "SELECT " +
            "  b.title, " +
            "  b.publisher, " +
            "  b.genre, " +
            "  COALESCE(a.name, '不明') as author_name, " +
            "  COUNT(b.id) as registration_count, " +
            "  COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) as completion_count, " +
            "  ROUND(" +
            "    COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) * 100.0 / " +
            "    NULLIF(COUNT(b.id), 0), 2" +
            "  ) as completion_rate, " +
            "  (COUNT(b.id) * 1.0 + COUNT(CASE WHEN rs.status = 'COMPLETED' THEN 1 END) * 2.0) as popularity_score " +
            "FROM books b " +
            "LEFT JOIN read_status rs ON b.id = rs.book_id " +
            "LEFT JOIN book_author ba ON b.id = ba.book_id " +
            "LEFT JOIN author a ON ba.author_id = a.id " +
            "WHERE b.created_at >= ? " +
            "GROUP BY b.title, b.publisher, b.genre, a.name " +
            "ORDER BY popularity_score DESC, completion_rate DESC " +
            "LIMIT 50",
            startDate);
        
        // ランキングアイテム作成
        List<BookRanking.RankingItem> rankings = new ArrayList<>();
        for (int i = 0; i < popularityData.size(); i++) {
            Map<String, Object> data = popularityData.get(i);
            
            BookRanking.RankingItem item = new BookRanking.RankingItem();
            item.setRank(i + 1);
            item.setTitle((String) data.get("title"));
            item.setPublisher((String) data.get("publisher"));
            item.setAuthorName((String) data.get("author_name"));
            item.setGenre((String) data.get("genre"));
            item.setRegistrationCount(((Number) data.get("registration_count")).intValue());
            item.setCompletionCount(((Number) data.get("completion_count")).intValue());
            item.setCompletionRate(((Number) data.get("completion_rate")).doubleValue());
            item.setPopularityScore(((Number) data.get("popularity_score")).doubleValue());
            
            rankings.add(item);
        }
        
        // メタデータ作成
        BookRanking.RankingMetadata metadata = new BookRanking.RankingMetadata();
        metadata.setPeriodStart(startDate);
        metadata.setPeriodEnd(endDate);
        metadata.setRankingCriteria("登録数 + 読了数の重み付けスコア");
        
        // 分析対象データ数取得
        Integer totalBooks = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT title, publisher) FROM books WHERE created_at >= ?",
            Integer.class, startDate);
        metadata.setTotalBooksAnalyzed(totalBooks);
        
        Integer totalUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM books WHERE created_at >= ?",
            Integer.class, startDate);
        metadata.setTotalUsersInvolved(totalUsers);
        
        // BookRanking作成
        BookRanking ranking = new BookRanking();
        ranking.setRankingType("POPULARITY");
        ranking.setGeneratedAt(LocalDateTime.now());
        ranking.setRankings(rankings);
        ranking.setMetadata(metadata);
        
        // 結果保存
        saveRanking(ranking, "POPULARITY_RANKING");
        
        System.out.println("人気度ランキング生成完了: TOP" + rankings.size());
        return RepeatStatus.FINISHED;
    }
    
    public void saveRanking(BookRanking ranking, String reportType) throws Exception {
        String rankingJson = objectMapper.writeValueAsString(ranking);
        
        jdbcTemplate.update(
            "INSERT INTO batch_statistics (report_type, target_date, data_json) " +
            "VALUES (?, ?, ?::jsonb) " +
            "ON CONFLICT (report_type, target_date) " +
            "DO UPDATE SET data_json = ?::jsonb, updated_at = NOW()",
            reportType, 
            LocalDate.now(), 
            rankingJson, 
            rankingJson
        );
    }
}