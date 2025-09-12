package com.library.management.service;

import com.library.management.entity.Book;
import com.library.management.entity.Author;
import com.library.management.entity.BookAuthor;
import com.library.management.entity.User;
import com.library.management.entity.ReadStatus;
import com.library.management.entity.Genre;
import com.library.management.repository.BookRepository;
import com.library.management.repository.AuthorRepository;
import com.library.management.repository.BookAuthorRepository;
import com.library.management.repository.UserRepository;
import com.library.management.repository.ReadStatusRepository;
import com.library.management.exception.BookNotFoundException;
import com.library.management.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

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
    
    @Autowired
    private ReadStatusRepository readStatusRepository;
    
    @Autowired
    private GenreService genreService;
    
    public List<Book> getAllBooks() {
        try {
            System.out.println("=== BookService.getAllBooks called ===");
            List<Book> books = bookRepository.findAllWithAuthors();
            System.out.println("BookRepository.findAllWithAuthors() returned: " + books.size() + " books");
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
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
            
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
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
            
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllBooksByUser(username);
        }
        return bookRepository.searchBooksByUser(currentUser.getId(), keyword.trim());
    }
    
    public List<Book> getBooksByReadStatus(String readStatusName) {
        ReadStatus readStatus = readStatusRepository.findByName(readStatusName)
            .orElseThrow(() -> new RuntimeException("Read status not found: " + readStatusName));
        
        List<Book> books = bookRepository.findByReadStatus(readStatus);
        // 著者情報を含む完全な情報を取得するために再取得
        return books.stream()
            .map(book -> bookRepository.findByIdWithAuthors(book.getId()).orElse(book))
            .collect(Collectors.toList());
    }
    
    public List<Book> getBooksByReadStatusAndUser(String readStatusName, String username) {
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        
        ReadStatus readStatus = readStatusRepository.findByName(readStatusName)
            .orElseThrow(() -> new RuntimeException("Read status not found: " + readStatusName));
            
        return bookRepository.findByUserIdAndReadStatus(currentUser.getId(), readStatus);
    }
    
    public Book saveBook(Book book, List<String> authorNames) {
        return saveBookWithGenre(book, authorNames, null);
    }
    
    public Book saveBookWithGenre(Book book, List<String> authorNames, Long genreId) {
        // ジャンルが指定されている場合は設定
        if (genreId != null) {
            Genre genre = genreService.getGenreById(genreId)
                    .orElseThrow(() -> new RuntimeException("Genre not found with id: " + genreId));
            book.setGenre(genre);
        }
        
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
        return updateBookWithGenre(id, bookDetails, authorNames, null);
    }
    
    public Book updateBookWithGenre(Long id, Book bookDetails, List<String> authorNames, Long genreId) {
        Optional<Book> optionalBook = bookRepository.findById(id);
        if (optionalBook.isPresent()) {
            Book book = optionalBook.get();
            book.setTitle(bookDetails.getTitle());
            book.setPublisher(bookDetails.getPublisher());
            book.setPublishedDate(bookDetails.getPublishedDate());
            book.setIsbn(bookDetails.getIsbn());
            book.setReadStatus(bookDetails.getReadStatus());
            
            return saveBookWithGenre(book, authorNames, genreId);
        }
        throw new BookNotFoundException("Book not found with id: " + id);
    }
    
    public void deleteBook(Long id) {
        if (bookRepository.existsById(id)) {
            bookAuthorRepository.deleteByBookId(id);
            bookRepository.deleteById(id);
        } else {
            throw new BookNotFoundException("Book not found with id: " + id);
        }
    }
    
    public Optional<Book> getBookByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn);
    }
    
    public boolean isOwner(Long bookId, String username) {
        try {
            Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException("Book not found with id: " + bookId));
            
            User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
            
            if (book.getUserId() == null) {
                return false;
            }
            
            return book.getUserId().equals(currentUser.getId());
        } catch (Exception e) {
            System.out.println("Error in isOwner check: " + e.getMessage());
            return false;
        }
    }
    
    public Book createBook(Book book, String username) {
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        
        book.setUserId(currentUser.getId());
        return bookRepository.save(book);
    }
    
    public Book createBookWithAuthors(Book book, List<String> authorNames, String username) {
        return createBookWithAuthorsAndGenre(book, authorNames, username, null);
    }
    
    public Book createBookWithAuthorsAndGenre(Book book, List<String> authorNames, String username, Long genreId) {
        User currentUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        
        book.setUserId(currentUser.getId());
        return saveBookWithGenre(book, authorNames, genreId);
    }
}