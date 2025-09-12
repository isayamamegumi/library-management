package com.library.management.repository;

import com.library.management.entity.ReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReadStatusRepository extends JpaRepository<ReadStatus, Long> {
    
    Optional<ReadStatus> findByName(String name);
}