package com.library.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "read_statuses")
public class ReadStatus {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Status name is required")
    @Size(max = 50, message = "Status name must be less than 50 characters")
    @Column(nullable = false, unique = true)
    private String name;
    
    @Size(max = 255, message = "Description must be less than 255 characters")
    private String description;
    
    public ReadStatus() {}
    
    public ReadStatus(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}