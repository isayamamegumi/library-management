package com.library.management.batch;

import com.library.management.entity.Book;
import com.library.management.repository.BookRepository;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Component
public class BookItemReader implements ItemReader<Book> {
    
    @Autowired
    private BookRepository bookRepository;
    
    private Iterator<Book> bookIterator;
    
    @Override
    public Book read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (bookIterator == null) {
            List<Book> books = bookRepository.findAll();
            bookIterator = books.iterator();
        }
        
        if (bookIterator.hasNext()) {
            return bookIterator.next();
        } else {
            return null;
        }
    }
}