package com.library.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public class BookRequest {
    
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be less than 255 characters")
    private String title;
    
    @Size(max = 255, message = "Publisher must be less than 255 characters")
    private String publisher;
    
    private LocalDate publishedDate;
    
    @Size(max = 13, message = "ISBN must be less than 13 characters")
    private String isbn;
    
    @Size(max = 50, message = "Read status must be less than 50 characters")
    private String readStatus;
    
    private List<String> authorNames;
    
    public BookRequest() {}
    
    public BookRequest(String title, String publisher, LocalDate publishedDate, 
                      String isbn, String readStatus, List<String> authorNames) {
        this.title = title;
        this.publisher = publisher;
        this.publishedDate = publishedDate;
        this.isbn = isbn;
        this.readStatus = readStatus;
        this.authorNames = authorNames;
    }
    
    // Getters and Setters
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
    
    public String getReadStatus() {
        return readStatus;
    }
    
    public void setReadStatus(String readStatus) {
        this.readStatus = readStatus;
    }
    
    public List<String> getAuthorNames() {
        return authorNames;
    }
    
    public void setAuthorNames(List<String> authorNames) {
        this.authorNames = authorNames;
    }
}