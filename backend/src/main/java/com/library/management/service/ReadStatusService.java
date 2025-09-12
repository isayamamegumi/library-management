package com.library.management.service;

import com.library.management.entity.ReadStatus;
import com.library.management.repository.ReadStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ReadStatusService {
    
    @Autowired
    private ReadStatusRepository readStatusRepository;
    
    public List<ReadStatus> getAllReadStatuses() {
        return readStatusRepository.findAll();
    }
    
    public Optional<ReadStatus> getReadStatusById(Long id) {
        return readStatusRepository.findById(id);
    }
    
    public Optional<ReadStatus> getReadStatusByName(String name) {
        return readStatusRepository.findByName(name);
    }
    
    public ReadStatus saveReadStatus(ReadStatus readStatus) {
        return readStatusRepository.save(readStatus);
    }
    
    public void deleteReadStatus(Long id) {
        readStatusRepository.deleteById(id);
    }
    
    public ReadStatus getDefaultReadStatus() {
        return readStatusRepository.findByName("未読")
            .orElseGet(() -> {
                ReadStatus defaultStatus = new ReadStatus("未読", "まだ読んでいない本");
                return readStatusRepository.save(defaultStatus);
            });
    }
}