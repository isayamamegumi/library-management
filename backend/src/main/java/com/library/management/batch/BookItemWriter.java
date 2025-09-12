package com.library.management.batch;

import com.library.management.entity.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class BookItemWriter implements ItemWriter<Book> {
    
    private static final Logger logger = LoggerFactory.getLogger(BookItemWriter.class);
    
    @Override
    public void write(Chunk<? extends Book> books) throws Exception {
        for (Book book : books) {
            logger.info("処理されたBook: ID={}, Title={}, Publisher={}", 
                       book.getId(), book.getTitle(), book.getPublisher());
        }
    }
}