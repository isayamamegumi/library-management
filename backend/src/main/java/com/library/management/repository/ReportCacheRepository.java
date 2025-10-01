package com.library.management.repository;

import com.library.management.entity.ReportCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportCacheRepository extends JpaRepository<ReportCache, Long> {

    Optional<ReportCache> findByCacheKeyAndIsValidTrue(String cacheKey);

    List<ReportCache> findByUserIdAndIsValidTrueOrderByCreatedAtDesc(Long userId);

    @Query("SELECT rc FROM ReportCache rc WHERE rc.userId = :userId " +
           "AND rc.reportType = :reportType AND rc.format = :format " +
           "AND rc.parameters = :parameters AND rc.isValid = true " +
           "AND (rc.expiresAt IS NULL OR rc.expiresAt > :currentTime) " +
           "ORDER BY rc.createdAt DESC")
    Optional<ReportCache> findValidCache(@Param("userId") Long userId,
                                        @Param("reportType") String reportType,
                                        @Param("format") String format,
                                        @Param("parameters") String parameters,
                                        @Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT rc FROM ReportCache rc WHERE rc.expiresAt IS NOT NULL " +
           "AND rc.expiresAt <= :currentTime AND rc.isValid = true")
    List<ReportCache> findExpiredCaches(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT rc FROM ReportCache rc WHERE rc.isValid = true " +
           "AND rc.lastAccessTime < :cutoffTime")
    List<ReportCache> findUnusedCaches(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT rc FROM ReportCache rc WHERE rc.cacheStatus = :status")
    List<ReportCache> findByStatus(@Param("status") String status);

    @Query("SELECT COUNT(rc) FROM ReportCache rc WHERE rc.userId = :userId AND rc.isValid = true")
    long countValidCachesByUser(@Param("userId") Long userId);

    @Query("SELECT SUM(rc.fileSizeBytes) FROM ReportCache rc WHERE rc.isValid = true")
    Long getTotalCacheSize();

    @Query("SELECT rc.reportType, COUNT(rc) as count FROM ReportCache rc " +
           "WHERE rc.isValid = true GROUP BY rc.reportType")
    List<Object[]> getCacheStatsByReportType();

    @Query("SELECT AVG(rc.hitCount) FROM ReportCache rc WHERE rc.isValid = true")
    Double getAverageHitCount();

    @Modifying
    @Query("UPDATE ReportCache rc SET rc.isValid = false WHERE rc.id IN :ids")
    void invalidateCaches(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE ReportCache rc SET rc.hitCount = rc.hitCount + 1, " +
           "rc.lastAccessTime = :accessTime WHERE rc.id = :id")
    void recordCacheHit(@Param("id") Long id, @Param("accessTime") LocalDateTime accessTime);

    @Query("SELECT rc FROM ReportCache rc WHERE rc.isValid = true " +
           "ORDER BY rc.hitCount DESC, rc.lastAccessTime DESC")
    List<ReportCache> findPopularCaches();

    @Query("SELECT rc FROM ReportCache rc WHERE rc.reportType = :reportType " +
           "AND rc.isValid = true AND rc.userId = :userId " +
           "ORDER BY rc.createdAt DESC")
    List<ReportCache> findUserCachesByType(@Param("userId") Long userId, @Param("reportType") String reportType);

    @Query("SELECT rc FROM ReportCache rc WHERE rc.createdAt BETWEEN :startDate AND :endDate " +
           "AND rc.isValid = true ORDER BY rc.createdAt DESC")
    List<ReportCache> findCachesByDateRange(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
}