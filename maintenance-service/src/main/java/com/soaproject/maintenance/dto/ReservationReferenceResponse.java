package com.soaproject.maintenance.dto;

import java.time.Instant;

public record ReservationReferenceResponse(
        String reservationId,
        String partId,
        int quantity,
        String status,
        Instant releasedAt) {}