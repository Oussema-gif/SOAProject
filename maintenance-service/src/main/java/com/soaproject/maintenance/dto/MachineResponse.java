package com.soaproject.maintenance.dto;

import java.time.Instant;

public record MachineResponse(
        String machineId,
        String name,
        String location,
        String model,
        String status,
        Instant createdAt,
        Instant updatedAt) {}