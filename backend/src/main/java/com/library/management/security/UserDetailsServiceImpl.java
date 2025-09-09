package com.library.management.security;

import com.library.management.entity.User;
import com.library.management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security用ユーザー詳細サービス実装
 * データベースからユーザー情報を取得してSpring Securityで使用可能な形式に変換
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * ユーザー名からUserDetailsを取得
     * @param username ユーザー名
     * @return UserDetails Spring Security用ユーザー詳細情報
     * @throws UsernameNotFoundException ユーザーが見つからない場合
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        return UserPrincipal.create(user);
    }
    
    /**
     * ユーザーIDからUserDetailsを取得（JWT認証で使用）
     * @param id ユーザーID
     * @return UserDetails Spring Security用ユーザー詳細情報
     * @throws UsernameNotFoundException ユーザーが見つからない場合
     */
    @Transactional
    public UserDetails loadUserById(Long id) throws UsernameNotFoundException {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        
        return UserPrincipal.create(user);
    }
}

