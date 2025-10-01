package com.library.management.repository;

import com.library.management.entity.ReportHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportHistoryRepository extends JpaRepository<ReportHistory, Long> {

    /**
     * ユーザーの帳票履歴を作成日時の降順で取得
     */
    List<ReportHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 特定ユーザーの特定IDのレポート取得
     */
    Optional<ReportHistory> findByIdAndUserId(Long id, Long userId);

    /**
     * 期限切れのレポート取得
     */
    List<ReportHistory> findByExpiresAtBeforeAndStatus(LocalDateTime dateTime, String status);

    /**
     * ユーザーの未完了レポート取得
     */
    @Query("SELECT r FROM ReportHistory r WHERE r.userId = :userId AND r.status IN ('GENERATING', 'PENDING')")
    List<ReportHistory> findPendingReportsByUserId(@Param("userId") Long userId);

    /**
     * レポートタイプ別の統計
     */
    @Query("SELECT r.reportType, COUNT(r) FROM ReportHistory r WHERE r.createdAt >= :since GROUP BY r.reportType")
    List<Object[]> getReportTypeStatistics(@Param("since") LocalDateTime since);

    /**
     * ユーザー別の生成回数上位
     */
    @Query("SELECT r.userId, COUNT(r) as count FROM ReportHistory r WHERE r.createdAt >= :since " +
           "GROUP BY r.userId ORDER BY count DESC")
    List<Object[]> getTopUsersByReportCount(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 失敗したレポート取得
     */
    List<ReportHistory> findByStatusAndCreatedAtAfter(String status, LocalDateTime createdAt);

    /**
     * 特定期間のレポート数取得
     */
    @Query("SELECT COUNT(r) FROM ReportHistory r WHERE r.createdAt BETWEEN :start AND :end")
    Long countReportsBetweenDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * ファイルサイズの統計
     */
    @Query("SELECT AVG(r.fileSize), MAX(r.fileSize), SUM(r.fileSize) FROM ReportHistory r WHERE r.fileSize IS NOT NULL")
    Object[] getFileSizeStatistics();
}