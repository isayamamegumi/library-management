package com.library.management.controller;

import com.library.management.dto.BookRequest;
import com.library.management.entity.Book;
import com.library.management.entity.User;
import com.library.management.service.BookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "http://localhost:3000")
public class BookController {
    
    @Autowired
    private BookService bookService;
    
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks(@RequestParam(required = false) String search,
                                                 @RequestParam(required = false) String readStatus) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            List<Book> books;
            
            if (search != null && !search.trim().isEmpty()) {
                books = bookService.searchBooksByUser(search, username);
            } else if (readStatus != null && !readStatus.trim().isEmpty()) {
                books = bookService.getBooksByReadStatusAndUser(readStatus, username);
            } else {
                books = bookService.getAllBooksByUser(username);
            }
            
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        try {
            Optional<Book> book = bookService.getBookById(id);
            return book.map(ResponseEntity::ok)
                      .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<Book> createBook(@Valid @RequestBody BookRequest bookRequest) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Book book = new Book(
                bookRequest.getTitle(),
                bookRequest.getPublisher(),
                bookRequest.getPublishedDate(),
                bookRequest.getIsbn(),
                bookRequest.getReadStatus()
            );
            
            Book savedBook = bookService.createBookWithAuthors(book, bookRequest.getAuthorNames(), username);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedBook);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("@bookService.isOwner(#id, authentication.name) or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, 
                                          @Valid @RequestBody BookRequest bookRequest) {
        try {
            Book book = new Book(
                bookRequest.getTitle(),
                bookRequest.getPublisher(),
                bookRequest.getPublishedDate(),
                bookRequest.getIsbn(),
                bookRequest.getReadStatus()
            );
            
            Book updatedBook = bookService.updateBook(id, book, bookRequest.getAuthorNames());
            return ResponseEntity.ok(updatedBook);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("@bookService.isOwner(#id, authentication.name) or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        try {
            bookService.deleteBook(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<Book> getBookByIsbn(@PathVariable String isbn) {
        try {
            Optional<Book> book = bookService.getBookByIsbn(isbn);
            return book.map(ResponseEntity::ok)
                      .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}