package com.library.management.service;

import com.library.management.entity.Author;
import com.library.management.repository.AuthorRepository;
import com.library.management.repository.BookAuthorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AuthorService {
    
    @Autowired
    private AuthorRepository authorRepository;
    
    @Autowired
    private BookAuthorRepository bookAuthorRepository;
    
    public List<Author> getAllAuthors() {
        return authorRepository.findAll();
    }
    
    public Optional<Author> getAuthorById(Long id) {
        return authorRepository.findById(id);
    }
    
    public List<Author> searchAuthors(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllAuthors();
        }
        return authorRepository.searchAuthors(keyword.trim());
    }
    
    public Author saveAuthor(Author author) {
        return authorRepository.save(author);
    }
    
    public Author updateAuthor(Long id, Author authorDetails) {
        Optional<Author> optionalAuthor = authorRepository.findById(id);
        if (optionalAuthor.isPresent()) {
            Author author = optionalAuthor.get();
            author.setName(authorDetails.getName());
            return authorRepository.save(author);
        }
        throw new RuntimeException("Author not found with id: " + id);
    }
    
    public void deleteAuthor(Long id) {
        if (authorRepository.existsById(id)) {
            bookAuthorRepository.deleteByAuthorId(id);
            authorRepository.deleteById(id);
        } else {
            throw new RuntimeException("Author not found with id: " + id);
        }
    }
    
    public Optional<Author> getAuthorByName(String name) {
        return authorRepository.findByName(name);
    }
}