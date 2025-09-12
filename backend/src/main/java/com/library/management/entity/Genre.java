package com.library.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

@Entity
@Table(name = "genres")
public class Genre {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Genre name is required")
    @Size(max = 100, message = "Genre name must be less than 100 characters")
    @Column(nullable = false, unique = true)
    private String name;
    
    @OneToMany(mappedBy = "genre", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Book> books;
    
    public Genre() {}
    
    public Genre(String name) {
        this.name = name;
    }
    
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    
    
    public List<Book> getBooks() {
        return books;
    }
    
    public void setBooks(List<Book> books) {
        this.books = books;
    }
    
    @Override
    public String toString() {
        return "Genre{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}