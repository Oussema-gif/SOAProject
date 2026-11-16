package com.soaproject.maintenance.controller;

import com.soaproject.maintenance.dto.LoginRequest;
import com.soaproject.maintenance.dto.LoginResponse;
import com.soaproject.maintenance.dto.RegisterRequest;
import com.soaproject.maintenance.dto.UserResponse;
import com.soaproject.maintenance.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal String username) {
        return authService.me(username);
    }
}