package com.library.management.repository;

import com.library.management.entity.Book;
import com.library.management.entity.ReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
    
    List<Book> findByTitleContainingIgnoreCase(String title);
    
    List<Book> findByPublisherContainingIgnoreCase(String publisher);
    
    Optional<Book> findByIsbn(String isbn);
    
    List<Book> findByReadStatus(ReadStatus readStatus);
    
    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN FETCH b.bookAuthors ba LEFT JOIN FETCH ba.author")
    List<Book> findAllWithAuthors();
    
    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN FETCH b.bookAuthors ba LEFT JOIN FETCH ba.author WHERE b.id = :id")
    Optional<Book> findByIdWithAuthors(@Param("id") Long id);
    
    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN FETCH b.bookAuthors ba LEFT JOIN FETCH ba.author a " +
           "WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(b.publisher) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Book> searchBooks(@Param("keyword") String keyword);
    
    List<Book> findByUserId(Long userId);
    
    List<Book> findByUserIdAndReadStatus(Long userId, ReadStatus readStatus);
    
    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN FETCH b.bookAuthors ba LEFT JOIN FETCH ba.author a " +
           "WHERE b.userId = :userId AND (" +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(b.publisher) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Book> searchBooksByUser(@Param("userId") Long userId, @Param("keyword") String keyword);
    
    @Query("SELECT DISTINCT b FROM Book b LEFT JOIN FETCH b.bookAuthors ba LEFT JOIN FETCH ba.author WHERE b.userId = :userId")
    List<Book> findByUserIdWithAuthors(@Param("userId") Long userId);

    /**
     * 重複ユーザー数をカウント
     */
    @Query("SELECT COUNT(DISTINCT b.userId) FROM Book b")
    long countDistinctUsers();

    /**
     * ユーザー別統計取得
     */
    @Query("SELECT b.userId, u.username, COUNT(b) FROM Book b " +
           "LEFT JOIN User u ON b.userId = u.id " +
           "GROUP BY b.userId, u.username " +
           "ORDER BY COUNT(b) DESC")
    List<Object[]> findUserStatistics();

    /**
     * ユーザー別書籍数をカウント
     */
    long countByUserId(Long userId);
}