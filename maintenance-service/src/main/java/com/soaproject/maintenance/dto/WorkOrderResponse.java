package com.soaproject.maintenance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderResponse(
        UUID id,
        String machineId,
        UUID alertId,
        UUID technicianId,
        String technicianName,
        String title,
        String description,
        String priority,
        String status,
        Instant completedAt,
        List<ReservationReferenceResponse> reservations,
        Instant createdAt,
        Instant updatedAt) {}