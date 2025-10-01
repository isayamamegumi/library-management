package com.library.management.repository;

import com.library.management.entity.ReportLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReportLogRepository extends JpaRepository<ReportLog, Long> {

    Page<ReportLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ReportLog> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);

    @Query("SELECT rl FROM ReportLog rl WHERE rl.userId = :userId " +
           "AND rl.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY rl.createdAt DESC")
    Page<ReportLog> findByUserIdAndDateRange(@Param("userId") Long userId,
                                            @Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate,
                                            Pageable pageable);

    @Query("SELECT rl FROM ReportLog rl WHERE rl.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY rl.createdAt DESC")
    Page<ReportLog> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate,
                                   Pageable pageable);

    List<ReportLog> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoffDate);

    @Query("SELECT rl FROM ReportLog rl WHERE rl.reportType = :reportType " +
           "AND rl.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY rl.createdAt DESC")
    List<ReportLog> findByReportTypeAndDateRange(@Param("reportType") String reportType,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(rl) FROM ReportLog rl WHERE rl.userId = :userId AND rl.status = :status")
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Query("SELECT COUNT(rl) FROM ReportLog rl WHERE rl.status = :status " +
           "AND rl.createdAt BETWEEN :startDate AND :endDate")
    long countByStatusAndDateRange(@Param("status") String status,
                                  @Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    @Query("SELECT AVG(rl.processingTimeMs) FROM ReportLog rl WHERE rl.status = 'SUCCESS' " +
           "AND rl.reportType = :reportType AND rl.createdAt BETWEEN :startDate AND :endDate")
    Double getAverageProcessingTime(@Param("reportType") String reportType,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    @Query("SELECT rl.reportType, COUNT(rl) as count FROM ReportLog rl " +
           "WHERE rl.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY rl.reportType ORDER BY count DESC")
    List<Object[]> getReportTypeStatistics(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    @Query("SELECT rl.status, COUNT(rl) as count FROM ReportLog rl " +
           "WHERE rl.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY rl.status")
    List<Object[]> getStatusStatistics(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    @Query("SELECT rl FROM ReportLog rl WHERE rl.status = 'ERROR' " +
           "AND rl.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY rl.createdAt DESC")
    List<ReportLog> findRecentErrors(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    @Query("SELECT rl.userId, COUNT(rl) as count FROM ReportLog rl " +
           "WHERE rl.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY rl.userId ORDER BY count DESC")
    List<Object[]> getUserActivityStatistics(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DATE(rl.createdAt) as date, COUNT(rl) as count FROM ReportLog rl " +
           "WHERE rl.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(rl.createdAt) ORDER BY date DESC")
    List<Object[]> getDailyStatistics(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    @Query("SELECT rl FROM ReportLog rl WHERE rl.scheduleId = :scheduleId " +
           "ORDER BY rl.createdAt DESC")
    List<ReportLog> findByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query("SELECT rl FROM ReportLog rl WHERE rl.templateId = :templateId " +
           "ORDER BY rl.createdAt DESC")
    List<ReportLog> findByTemplateId(@Param("templateId") Long templateId);
}