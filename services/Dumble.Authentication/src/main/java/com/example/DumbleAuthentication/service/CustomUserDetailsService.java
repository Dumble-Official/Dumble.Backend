package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        String authority = "ROLE_" + user.getUserType().name();
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

        // Google users have no password; provide a placeholder that cannot match any
        // BCrypt hash
        String password = user.getPasswordHash() != null ? user.getPasswordHash() : "";

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                password,
                user.isActive(),
                true,
                true,
                true,
                authorities);
    }
}
