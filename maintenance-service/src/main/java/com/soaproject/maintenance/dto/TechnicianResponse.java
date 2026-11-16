package com.soaproject.maintenance.dto;

import java.time.Instant;
import java.util.UUID;

public record TechnicianResponse(
        UUID id,
        String fullName,
        String email,
        UUID userId,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}