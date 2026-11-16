package com.soaproject.maintenance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        String machineId,
        String metricType,
        String severity,
        BigDecimal observedValue,
        BigDecimal thresholdValue,
        String message,
        String status,
        String sourceEventId,
        Instant occurredAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {}