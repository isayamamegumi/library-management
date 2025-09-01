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

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "http://localhost:3000")
public class BookController {
    
    @Autowired
    private BookService bookService;
    
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks(@RequestParam(required = false) String search,
                                                 @RequestParam(required = false) String readStatus) {
        try {
            System.out.println("=== BookController.getAllBooks called ===");
            System.out.println("Search: " + search);
            System.out.println("ReadStatus: " + readStatus);
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            System.out.println("Authentication: " + auth);
            System.out.println("Authentication name: " + (auth != null ? auth.getName() : "null"));
            System.out.println("Authentication authenticated: " + (auth != null ? auth.isAuthenticated() : "null"));
            System.out.println("Authentication principal: " + (auth != null ? auth.getPrincipal() : "null"));
            
            String username = null;
            
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                username = auth.getName();
                System.out.println("Authenticated user: " + username);
            } else {
                System.out.println("User not authenticated or is anonymous");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            List<Book> books;
            
            if (search != null && !search.trim().isEmpty()) {
                System.out.println("Searching books with: " + search + " for user: " + username);
                books = bookService.searchBooksByUser(search, username);
            } else if (readStatus != null && !readStatus.trim().isEmpty()) {
                System.out.println("Getting books by status: " + readStatus + " for user: " + username);
                books = bookService.getBooksByReadStatusAndUser(readStatus, username);
            } else {
                System.out.println("Getting all books for user: " + username);
                books = bookService.getAllBooksByUser(username);
            }
            
            System.out.println("Found " + books.size() + " books");
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            System.out.println("=== ERROR in BookController.getAllBooks ===");
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
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
    
    @PostMapping
    public ResponseEntity<Book> createBook(@Valid @RequestBody BookRequest bookRequest) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            
            String username = null;
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                username = auth.getName();
            }
            
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
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