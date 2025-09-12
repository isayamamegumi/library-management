package com.library.management.batch;

import com.library.management.entity.User;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class UserReader {
    
    @Autowired
    private DataSource dataSource;
    
    @Bean(name = "userItemReader")
    public JdbcCursorItemReader<User> userItemReader() {
        return new JdbcCursorItemReaderBuilder<User>()
                .name("userItemReader")
                .dataSource(dataSource)
                .sql("SELECT id, username, email, password_hash as password, created_at FROM users WHERE id IN (SELECT DISTINCT user_id FROM books)")
                .rowMapper(new BeanPropertyRowMapper<>(User.class))
                .build();
    }
}