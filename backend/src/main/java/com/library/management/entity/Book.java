package com.library.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "books")
public class Book {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be less than 255 characters")
    @Column(nullable = false)
    private String title;
    
    @Size(max = 255, message = "Publisher must be less than 255 characters")
    private String publisher;
    
    @Column(name = "published_date")
    private LocalDate publishedDate;
    
    @Size(max = 13, message = "ISBN must be less than 13 characters")
    @Column(unique = true)
    private String isbn;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "read_status_id")
    private ReadStatus readStatus;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<BookAuthor> bookAuthors = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "genre_id")
    private Genre genre;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public Book() {}
    
    public Book(String title, String publisher, LocalDate publishedDate, String isbn, ReadStatus readStatus) {
        this.title = title;
        this.publisher = publisher;
        this.publishedDate = publishedDate;
        this.isbn = isbn;
        this.readStatus = readStatus;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getPublisher() {
        return publisher;
    }
    
    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }
    
    public LocalDate getPublishedDate() {
        return publishedDate;
    }
    
    public void setPublishedDate(LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }
    
    public String getIsbn() {
        return isbn;
    }
    
    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }
    
    public ReadStatus getReadStatus() {
        return readStatus;
    }
    
    public void setReadStatus(ReadStatus readStatus) {
        this.readStatus = readStatus;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public List<BookAuthor> getBookAuthors() {
        return bookAuthors;
    }
    
    public void setBookAuthors(List<BookAuthor> bookAuthors) {
        this.bookAuthors = bookAuthors;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Genre getGenre() {
        return genre;
    }
    
    public void setGenre(Genre genre) {
        this.genre = genre;
    }
}