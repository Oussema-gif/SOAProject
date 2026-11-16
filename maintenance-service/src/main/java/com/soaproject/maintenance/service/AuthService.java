package com.soaproject.maintenance.service;

import com.soaproject.maintenance.dto.LoginRequest;
import com.soaproject.maintenance.dto.LoginResponse;
import com.soaproject.maintenance.dto.RegisterRequest;
import com.soaproject.maintenance.dto.UserResponse;
import com.soaproject.maintenance.entity.UserEntity;
import com.soaproject.maintenance.exception.DuplicateResourceException;
import com.soaproject.maintenance.exception.ResourceNotFoundException;
import com.soaproject.maintenance.repository.UserRepository;
import com.soaproject.maintenance.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final long expirationMs;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.expirationMs = expirationMs;
    }

    public LoginResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        UserEntity user = userRepository.findByUsernameIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User vanished: " + auth.getName()));
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new LoginResponse(token, "Bearer", expirationMs / 1000,
                user.getUsername(), user.getRole().name());
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new DuplicateResourceException("Username already exists: " + req.username());
        }
        UserEntity user = new UserEntity();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(req.role());
        user.setEnabled(true);
        UserEntity saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getUsername(),
                saved.getRole().name(), saved.isEnabled());
    }

    public UserResponse me(String username) {
        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown user: " + username));
        return new UserResponse(user.getId(), user.getUsername(),
                user.getRole().name(), user.isEnabled());
    }
}