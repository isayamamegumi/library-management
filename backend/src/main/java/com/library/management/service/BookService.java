package com.library.management.service;

import com.library.management.entity.Book;
import com.library.management.entity.Author;
import com.library.management.entity.BookAuthor;
import com.library.management.entity.User;
import com.library.management.repository.BookRepository;
import com.library.management.repository.AuthorRepository;
import com.library.management.repository.BookAuthorRepository;
import com.library.management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Service
@Transactional
public class BookService {
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private AuthorRepository authorRepository;
    
    @Autowired
    private BookAuthorRepository bookAuthorRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    public List<Book> getAllBooks() {
        try {
            System.out.println("=== BookService.getAllBooks called ===");
            List<Book> books = bookRepository.findAll();  // 一時的に単純なfindAll()を使用
            System.out.println("BookRepository.findAll() returned: " + books.size() + " books");
            return books;
        } catch (Exception e) {
            System.out.println("=== ERROR in BookService.getAllBooks ===");
            e.printStackTrace();
            throw e;
        }
    }
    
    public List<Book> getAllBooksByUser(String username) {
        try {
            System.out.println("=== BookService.getAllBooksByUser called for user: " + username + " ===");
            User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
            
            List<Book> books = bookRepository.findByUserIdWithAuthors(currentUser.getId());
            System.out.println("Found " + books.size() + " books for user: " + username);
            return books;
        } catch (Exception e) {
            System.out.println("=== ERROR in BookService.getAllBooksByUser ===");
            e.printStackTrace();
            throw e;
        }
    }
    
    public Optional<Book> getBookById(Long id) {
        return bookRepository.findByIdWithAuthors(id);
    }
    
    public List<Book> searchBooks(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllBooks();
        }
        return bookRepository.searchBooks(keyword.trim());
    }
    
    public List<Book> searchBooksByUser(String keyword, String username) {
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
            
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllBooksByUser(username);
        }
        return bookRepository.searchBooksByUser(currentUser.getId(), keyword.trim());
    }
    
    public List<Book> getBooksByReadStatus(String readStatus) {
        return bookRepository.findByReadStatus(readStatus);
    }
    
    public List<Book> getBooksByReadStatusAndUser(String readStatus, String username) {
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
            
        return bookRepository.findByUserIdAndReadStatus(currentUser.getId(), readStatus);
    }
    
    public Book saveBook(Book book, List<String> authorNames) {
        Book savedBook = bookRepository.save(book);
        
        if (authorNames != null && !authorNames.isEmpty()) {
            bookAuthorRepository.deleteByBookId(savedBook.getId());
            
            Set<String> uniqueAuthorNames = new HashSet<>(authorNames);
            
            for (String authorName : uniqueAuthorNames) {
                if (authorName != null && !authorName.trim().isEmpty()) {
                    Author author = authorRepository.findByName(authorName.trim())
                            .orElseGet(() -> authorRepository.save(new Author(authorName.trim())));
                    
                    BookAuthor bookAuthor = new BookAuthor(savedBook.getId(), author.getId());
                    bookAuthorRepository.save(bookAuthor);
                }
            }
        }
        
        return bookRepository.findByIdWithAuthors(savedBook.getId()).orElse(savedBook);
    }
    
    public Book updateBook(Long id, Book bookDetails, List<String> authorNames) {
        Optional<Book> optionalBook = bookRepository.findById(id);
        if (optionalBook.isPresent()) {
            Book book = optionalBook.get();
            book.setTitle(bookDetails.getTitle());
            book.setPublisher(bookDetails.getPublisher());
            book.setPublishedDate(bookDetails.getPublishedDate());
            book.setIsbn(bookDetails.getIsbn());
            book.setReadStatus(bookDetails.getReadStatus());
            
            return saveBook(book, authorNames);
        }
        throw new RuntimeException("Book not found with id: " + id);
    }
    
    public void deleteBook(Long id) {
        if (bookRepository.existsById(id)) {
            bookAuthorRepository.deleteByBookId(id);
            bookRepository.deleteById(id);
        } else {
            throw new RuntimeException("Book not found with id: " + id);
        }
    }
    
    public Optional<Book> getBookByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn);
    }
    
    public boolean isOwner(Long bookId, String username) {
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new RuntimeException("Book not found with id: " + bookId));
        
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        return book.getUserId().equals(currentUser.getId());
    }
    
    public Book createBook(Book book, String username) {
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        book.setUserId(currentUser.getId());
        return bookRepository.save(book);
    }
    
    public Book createBookWithAuthors(Book book, List<String> authorNames, String username) {
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        book.setUserId(currentUser.getId());
        return saveBook(book, authorNames);
    }
}