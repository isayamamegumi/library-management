package com.library.management.controller;

import com.library.management.entity.ReadStatus;
import com.library.management.service.ReadStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/read-statuses")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class ReadStatusController {
    
    @Autowired
    private ReadStatusService readStatusService;
    
    @GetMapping
    public ResponseEntity<List<ReadStatus>> getAllReadStatuses() {
        List<ReadStatus> readStatuses = readStatusService.getAllReadStatuses();
        return ResponseEntity.ok(readStatuses);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ReadStatus> getReadStatusById(@PathVariable Long id) {
        return readStatusService.getReadStatusById(id)
            .map(readStatus -> ResponseEntity.ok().body(readStatus))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<ReadStatus> createReadStatus(@RequestBody ReadStatus readStatus) {
        ReadStatus savedReadStatus = readStatusService.saveReadStatus(readStatus);
        return ResponseEntity.ok(savedReadStatus);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ReadStatus> updateReadStatus(@PathVariable Long id, @RequestBody ReadStatus readStatusDetails) {
        return readStatusService.getReadStatusById(id)
            .map(readStatus -> {
                readStatus.setName(readStatusDetails.getName());
                readStatus.setDescription(readStatusDetails.getDescription());
                return ResponseEntity.ok(readStatusService.saveReadStatus(readStatus));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReadStatus(@PathVariable Long id) {
        return readStatusService.getReadStatusById(id)
            .map(readStatus -> {
                readStatusService.deleteReadStatus(id);
                return ResponseEntity.ok().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}