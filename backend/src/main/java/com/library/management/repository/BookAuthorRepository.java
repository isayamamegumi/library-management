package com.library.management.repository;

import com.library.management.entity.BookAuthor;
import com.library.management.entity.BookAuthorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookAuthorRepository extends JpaRepository<BookAuthor, BookAuthorId> {
    
    List<BookAuthor> findByBookId(Long bookId);
    
    List<BookAuthor> findByAuthorId(Long authorId);
    
    @Modifying
    @Query("DELETE FROM BookAuthor ba WHERE ba.bookId = :bookId")
    void deleteByBookId(@Param("bookId") Long bookId);
    
    @Modifying
    @Query("DELETE FROM BookAuthor ba WHERE ba.authorId = :authorId")
    void deleteByAuthorId(@Param("authorId") Long authorId);
}