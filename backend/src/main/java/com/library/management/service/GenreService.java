package com.library.management.service;

import com.library.management.entity.Genre;
import com.library.management.repository.GenreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class GenreService {
    
    @Autowired
    private GenreRepository genreRepository;
    
    public List<Genre> getAllGenres() {
        return genreRepository.findAllOrderByName();
    }
    
    public Optional<Genre> getGenreById(Long id) {
        return genreRepository.findById(id);
    }
    
    public Optional<Genre> getGenreByName(String name) {
        return genreRepository.findByName(name);
    }
    
    public Genre createGenre(Genre genre) {
        if (genreRepository.existsByName(genre.getName())) {
            throw new RuntimeException("Genre with name '" + genre.getName() + "' already exists");
        }
        return genreRepository.save(genre);
    }
    
    public Genre updateGenre(Long id, Genre genre) {
        Genre existingGenre = genreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Genre not found with id: " + id));
        
        if (!existingGenre.getName().equals(genre.getName()) && 
            genreRepository.existsByName(genre.getName())) {
            throw new RuntimeException("Genre with name '" + genre.getName() + "' already exists");
        }
        
        existingGenre.setName(genre.getName());
        return genreRepository.save(existingGenre);
    }
    
    public void deleteGenre(Long id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Genre not found with id: " + id));
        
        if (!genre.getBooks().isEmpty()) {
            throw new RuntimeException("Cannot delete genre that has associated books");
        }
        
        genreRepository.delete(genre);
    }
    
    public List<Genre> searchGenres(String name) {
        return genreRepository.findByNameContainingIgnoreCase(name);
    }
    
    /**
     * ジャンル名でGenreを取得、存在しなければ作成
     */
    public Genre getOrCreateGenre(String name) {
        return genreRepository.findByName(name)
                .orElseGet(() -> {
                    Genre newGenre = new Genre(name);
                    return genreRepository.save(newGenre);
                });
    }
}