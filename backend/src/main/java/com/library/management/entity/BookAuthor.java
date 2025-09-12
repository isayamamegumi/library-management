package com.library.management.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "book_authors")
@IdClass(BookAuthorId.class)
public class BookAuthor {
    
    @Id
    @Column(name = "book_id")
    private Long bookId;
    
    @Id
    @Column(name = "author_id")
    private Long authorId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", insertable = false, updatable = false)
    @JsonBackReference
    private Book book;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    @JsonManagedReference
    private Author author;
    
    public BookAuthor() {}
    
    public BookAuthor(Long bookId, Long authorId) {
        this.bookId = bookId;
        this.authorId = authorId;
    }
    
    public BookAuthor(Book book, Author author) {
        this.book = book;
        this.author = author;
        this.bookId = book.getId();
        this.authorId = author.getId();
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
    
    public Book getBook() {
        return book;
    }
    
    public void setBook(Book book) {
        this.book = book;
        if (book != null) {
            this.bookId = book.getId();
        }
    }
    
    public Author getAuthor() {
        return author;
    }
    
    public void setAuthor(Author author) {
        this.author = author;
        if (author != null) {
            this.authorId = author.getId();
        }
    }
}