package com.library.management.controller;

import com.library.management.entity.Role;
import com.library.management.entity.User;
import com.library.management.repository.RoleRepository;
import com.library.management.repository.UserRepository;
import com.library.management.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import com.library.management.dto.UserRegistrationRequest;
import com.library.management.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            System.out.println("=== JWT Login attempt for user: " + loginRequest.getUsername() + " ===");
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Spring SecurityのUserPrincipalから情報を取得
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findByUsername(userPrincipal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // JWTトークンを生成
            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
            
            System.out.println("User authenticated: " + user.getUsername());
            System.out.println("JWT tokens generated successfully");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successful");
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("role", user.getRole().getName());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid credentials");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRegistrationRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Email is already in use!"));
        }

        Role userRole = roleRepository.findByName("user")
            .orElseGet(() -> {
                Role newRole = new Role("user");
                return roleRepository.save(newRole);
            });

        User user = new User(
            signUpRequest.getUsername(),
            signUpRequest.getEmail(),
            passwordEncoder.encode(signUpRequest.getPassword()),
            userRole
        );

        User savedUser = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User registered successfully");
        response.put("username", savedUser.getUsername());
        response.put("email", savedUser.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshRequest) {
        try {
            String refreshToken = refreshRequest.getRefreshToken();
            
            if (jwtUtil.validateJwtToken(refreshToken) && jwtUtil.isRefreshToken(refreshToken)) {
                String username = jwtUtil.getUsernameFromJwtToken(refreshToken);
                
                // ユーザーが存在するか確認し、UserDetailsを取得
                User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
                
                // 新しいアクセストークンを生成
                String newAccessToken = jwtUtil.generateAccessToken(user);
                
                Map<String, Object> response = new HashMap<>();
                response.put("accessToken", newAccessToken);
                response.put("tokenType", "Bearer");
                response.put("message", "Token refreshed successfully");
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid refresh token"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Token refresh failed"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        // JWT認証では、クライアント側でトークンを削除するだけで十分
        // サーバー側では特別な処理は不要（ステートレス）
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class SignUpRequest {
        private String username;
        private String email;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}