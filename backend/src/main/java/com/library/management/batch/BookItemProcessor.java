package com.library.management.batch;

import com.library.management.entity.Book;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class BookItemProcessor implements ItemProcessor<Book, Book> {
    
    @Override
    public Book process(Book book) throws Exception {
        Book processedBook = new Book();
        processedBook.setId(book.getId());
        processedBook.setTitle(book.getTitle().toUpperCase());
        processedBook.setPublisher(book.getPublisher());
        processedBook.setPublishedDate(book.getPublishedDate());
        processedBook.setIsbn(book.getIsbn());
        processedBook.setReadStatus(book.getReadStatus());
        processedBook.setUserId(book.getUserId());
        processedBook.setCreatedAt(book.getCreatedAt());
        processedBook.setBookAuthors(book.getBookAuthors());
        
        return processedBook;
    }
}