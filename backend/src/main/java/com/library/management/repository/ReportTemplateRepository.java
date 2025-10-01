package com.library.management.repository;

import com.library.management.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 帳票テンプレートリポジトリ
 */
@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    /**
     * アクティブなテンプレート一覧取得
     */
    List<ReportTemplate> findByIsActiveTrueOrderByCreatedAtDesc();

    /**
     * タイプとカテゴリでテンプレート検索
     */
    List<ReportTemplate> findByTypeAndCategoryAndIsActiveTrueOrderByIsDefaultDescCreatedAtDesc(
        String type, String category);

    /**
     * デフォルトテンプレート取得
     */
    Optional<ReportTemplate> findByTypeAndCategoryAndIsDefaultTrueAndIsActiveTrue(
        String type, String category);

    /**
     * ユーザーのカスタムテンプレート取得
     */
    List<ReportTemplate> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    /**
     * ユーザーがアクセス可能なテンプレート取得（システム標準 + 自分のカスタム）
     */
    @Query("SELECT t FROM ReportTemplate t WHERE t.isActive = true AND " +
           "(t.userId IS NULL OR t.userId = :userId) AND " +
           "(:type IS NULL OR t.type = :type) AND " +
           "(:category IS NULL OR t.category = :category) " +
           "ORDER BY t.isDefault DESC, t.createdAt DESC")
    List<ReportTemplate> findAccessibleTemplates(
        @Param("userId") Long userId,
        @Param("type") String type,
        @Param("category") String category);

    /**
     * 同名テンプレート存在チェック
     */
    boolean existsByNameAndUserIdAndIsActiveTrue(String name, Long userId);

    /**
     * デフォルトテンプレート存在チェック
     */
    boolean existsByTypeAndCategoryAndIsDefaultTrueAndIsActiveTrue(String type, String category);
}