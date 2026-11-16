package com.soaproject.maintenance.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String role,
        boolean enabled) {}