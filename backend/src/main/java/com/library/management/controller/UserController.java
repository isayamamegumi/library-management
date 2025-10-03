package com.library.management.controller;

import com.library.management.entity.User;
import com.library.management.repository.UserRepository;
import com.library.management.dto.UserUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:3000")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            User userData = user.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", userData.getId());
            response.put("username", userData.getUsername());
            response.put("email", userData.getEmail());
            response.put("role", userData.getRole().getName());
            response.put("createdAt", userData.getCreatedAt());
            return ResponseEntity.ok(response);
        }
        
        return ResponseEntity.notFound().build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        com.library.management.security.UserPrincipal userPrincipal =
            (com.library.management.security.UserPrincipal) auth.getPrincipal();

        // UserPrincipalからユーザーIDを取得し、データベースから完全なユーザー情報を取得
        Optional<User> userOpt = userRepository.findById(userPrincipal.getId());
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User currentUser = userOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("id", currentUser.getId());
        response.put("username", currentUser.getUsername());
        response.put("email", currentUser.getEmail());
        response.put("role", currentUser.getRole().getName());
        response.put("createdAt", currentUser.getCreatedAt());

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@Valid @RequestBody UserUpdateRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        com.library.management.security.UserPrincipal userPrincipal =
            (com.library.management.security.UserPrincipal) auth.getPrincipal();

        Optional<User> optionalUser = userRepository.findById(userPrincipal.getId());
        if (!optionalUser.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email is already in use"));
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User updatedUser = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("id", updatedUser.getId());
        response.put("username", updatedUser.getUsername());
        response.put("email", updatedUser.getEmail());
        response.put("role", updatedUser.getRole().getName());
        response.put("message", "User updated successfully");

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("#id == authentication.principal.id or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        
        Optional<User> optionalUser = userRepository.findById(id);
        if (!optionalUser.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = optionalUser.get();
        
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email is already in use"));
            }
            user.setEmail(request.getEmail());
        }
        
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        User updatedUser = userRepository.save(user);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", updatedUser.getId());
        response.put("username", updatedUser.getUsername());
        response.put("email", updatedUser.getEmail());
        response.put("role", updatedUser.getRole().getName());
        response.put("message", "User updated successfully");
        
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();
        
        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cannot delete your own account"));
        }
        
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }
}