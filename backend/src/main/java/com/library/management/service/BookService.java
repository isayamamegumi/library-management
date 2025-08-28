package com.library.management.service;

import com.library.management.entity.Book;
import com.library.management.entity.Author;
import com.library.management.entity.BookAuthor;
import com.library.management.repository.BookRepository;
import com.library.management.repository.AuthorRepository;
import com.library.management.repository.BookAuthorRepository;
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
    
    public List<Book> getAllBooks() {
        return bookRepository.findAllWithAuthors();
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
    
    public List<Book> getBooksByReadStatus(String readStatus) {
        return bookRepository.findByReadStatus(readStatus);
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
}