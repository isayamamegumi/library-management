package com.library.management.repository;

import com.library.management.entity.ReportDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportDistributionRepository extends JpaRepository<ReportDistribution, Long> {

    List<ReportDistribution> findByUserIdAndIsActiveTrue(Long userId);

    List<ReportDistribution> findByUserIdAndStatusAndIsActiveTrue(Long userId, String status);

    List<ReportDistribution> findByScheduleIdAndIsActiveTrue(Long scheduleId);

    @Query("SELECT rd FROM ReportDistribution rd WHERE rd.userId = :userId " +
           "AND rd.name = :name AND rd.isActive = true")
    Optional<ReportDistribution> findByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);

    @Query("SELECT COUNT(rd) FROM ReportDistribution rd WHERE rd.userId = :userId AND rd.isActive = true")
    long countActiveDistributionsByUser(@Param("userId") Long userId);

    @Query("SELECT rd FROM ReportDistribution rd WHERE rd.distributionType = :distributionType " +
           "AND rd.status = 'ACTIVE' AND rd.isActive = true")
    List<ReportDistribution> findByDistributionTypeAndActive(@Param("distributionType") String distributionType);

    List<ReportDistribution> findByStatusAndIsActiveTrue(String status);

    @Query("SELECT rd FROM ReportDistribution rd WHERE rd.scheduleId IN :scheduleIds " +
           "AND rd.status = 'ACTIVE' AND rd.isActive = true")
    List<ReportDistribution> findByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);
}