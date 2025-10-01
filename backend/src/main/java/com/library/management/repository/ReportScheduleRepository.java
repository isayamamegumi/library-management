package com.library.management.repository;

import com.library.management.entity.ReportSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {

    List<ReportSchedule> findByUserIdAndIsActiveTrue(Long userId);

    List<ReportSchedule> findByUserIdAndStatusAndIsActiveTrue(Long userId, String status);

    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.nextRunTime <= :currentTime " +
           "AND rs.status = 'ACTIVE' AND rs.isActive = true")
    List<ReportSchedule> findSchedulesToRun(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.lastRunTime IS NULL " +
           "AND rs.status = 'ACTIVE' AND rs.isActive = true")
    List<ReportSchedule> findNeverRunSchedules();

    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.userId = :userId " +
           "AND rs.name = :name AND rs.isActive = true")
    Optional<ReportSchedule> findByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);

    @Query("SELECT COUNT(rs) FROM ReportSchedule rs WHERE rs.userId = :userId AND rs.isActive = true")
    long countActiveSchedulesByUser(@Param("userId") Long userId);

    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.scheduleType = :scheduleType " +
           "AND rs.status = 'ACTIVE' AND rs.isActive = true")
    List<ReportSchedule> findByScheduleTypeAndActive(@Param("scheduleType") String scheduleType);

    List<ReportSchedule> findByStatusAndIsActiveTrue(String status);

    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.nextRunTime BETWEEN :startTime AND :endTime " +
           "AND rs.status = 'ACTIVE' AND rs.isActive = true ORDER BY rs.nextRunTime ASC")
    List<ReportSchedule> findSchedulesInTimeRange(@Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);
}