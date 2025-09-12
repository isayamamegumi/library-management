package com.library.management.controller;

import com.library.management.entity.Genre;
import com.library.management.service.GenreService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
@CrossOrigin(origins = "http://localhost:3000")
public class GenreController {
    
    @Autowired
    private GenreService genreService;
    
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<Genre>> getAllGenres() {
        try {
            List<Genre> genres = genreService.getAllGenres();
            return ResponseEntity.ok(genres);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<Genre> getGenreById(@PathVariable Long id) {
        try {
            return genreService.getGenreById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/search")
    public ResponseEntity<List<Genre>> searchGenres(@RequestParam(required = false) String search) {
        try {
            List<Genre> genres;
            if (search != null && !search.trim().isEmpty()) {
                genres = genreService.searchGenres(search);
            } else {
                genres = genreService.getAllGenres();
            }
            return ResponseEntity.ok(genres);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<Genre> createGenre(@Valid @RequestBody Genre genre) {
        try {
            Genre createdGenre = genreService.createGenre(genre);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdGenre);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public ResponseEntity<Genre> updateGenre(@PathVariable Long id, @Valid @RequestBody Genre genre) {
        try {
            Genre updatedGenre = genreService.updateGenre(id, genre);
            return ResponseEntity.ok(updatedGenre);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGenre(@PathVariable Long id) {
        try {
            genreService.deleteGenre(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}