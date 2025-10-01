package com.library.management.repository;

import com.library.management.entity.ReportPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportPermissionRepository extends JpaRepository<ReportPermission, Long> {

    List<ReportPermission> findByUserIdAndIsActiveTrue(Long userId);

    List<ReportPermission> findByUserIdAndResourceTypeAndIsActiveTrue(Long userId, String resourceType);

    @Query("SELECT rp FROM ReportPermission rp WHERE rp.userId = :userId " +
           "AND rp.resourceType = :resourceType AND rp.resourceId = :resourceId " +
           "AND rp.permission = :permission AND rp.isActive = true " +
           "AND (rp.expiresAt IS NULL OR rp.expiresAt > :currentTime)")
    Optional<ReportPermission> findValidPermission(@Param("userId") Long userId,
                                                  @Param("resourceType") String resourceType,
                                                  @Param("resourceId") Long resourceId,
                                                  @Param("permission") String permission,
                                                  @Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT rp FROM ReportPermission rp WHERE rp.userId = :userId " +
           "AND rp.resourceType = :resourceType " +
           "AND (rp.resourceId = :resourceId OR rp.resourceId IS NULL) " +
           "AND rp.isActive = true " +
           "AND (rp.expiresAt IS NULL OR rp.expiresAt > :currentTime)")
    List<ReportPermission> findUserPermissions(@Param("userId") Long userId,
                                              @Param("resourceType") String resourceType,
                                              @Param("resourceId") Long resourceId,
                                              @Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT DISTINCT rp.permission FROM ReportPermission rp WHERE rp.userId = :userId " +
           "AND rp.resourceType = :resourceType " +
           "AND (rp.resourceId = :resourceId OR rp.resourceId IS NULL) " +
           "AND rp.isActive = true " +
           "AND (rp.expiresAt IS NULL OR rp.expiresAt > :currentTime)")
    List<String> findUserPermissionNames(@Param("userId") Long userId,
                                        @Param("resourceType") String resourceType,
                                        @Param("resourceId") Long resourceId,
                                        @Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT rp FROM ReportPermission rp WHERE rp.resourceType = :resourceType " +
           "AND rp.resourceId = :resourceId AND rp.isActive = true")
    List<ReportPermission> findByResource(@Param("resourceType") String resourceType,
                                         @Param("resourceId") Long resourceId);

    @Query("SELECT rp FROM ReportPermission rp WHERE rp.grantedBy = :grantedBy " +
           "AND rp.isActive = true ORDER BY rp.createdAt DESC")
    List<ReportPermission> findByGrantedBy(@Param("grantedBy") Long grantedBy);

    @Query("SELECT rp FROM ReportPermission rp WHERE rp.expiresAt IS NOT NULL " +
           "AND rp.expiresAt <= :currentTime AND rp.isActive = true")
    List<ReportPermission> findExpiredPermissions(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT COUNT(rp) FROM ReportPermission rp WHERE rp.userId = :userId " +
           "AND rp.resourceType = :resourceType AND rp.isActive = true")
    long countUserPermissions(@Param("userId") Long userId, @Param("resourceType") String resourceType);

    @Query("SELECT rp FROM ReportPermission rp WHERE rp.userId = :userId " +
           "AND rp.resourceType = :resourceType AND rp.resourceId = :resourceId " +
           "AND rp.permission = :permission AND rp.isActive = true")
    Optional<ReportPermission> findExistingPermission(@Param("userId") Long userId,
                                                     @Param("resourceType") String resourceType,
                                                     @Param("resourceId") Long resourceId,
                                                     @Param("permission") String permission);
}