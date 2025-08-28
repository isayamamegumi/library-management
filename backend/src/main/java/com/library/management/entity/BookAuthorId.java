package com.library.management.entity;

import java.io.Serializable;
import java.util.Objects;

public class BookAuthorId implements Serializable {
    
    private Long bookId;
    private Long authorId;
    
    public BookAuthorId() {}
    
    public BookAuthorId(Long bookId, Long authorId) {
        this.bookId = bookId;
        this.authorId = authorId;
    }
    
    // Getters and Setters
    public Long getBookId() {
        return bookId;
    }
    
    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }
    
    public Long getAuthorId() {
        return authorId;
    }
    
    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookAuthorId that = (BookAuthorId) o;
        return Objects.equals(bookId, that.bookId) && Objects.equals(authorId, that.authorId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(bookId, authorId);
    }
}