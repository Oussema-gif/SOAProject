package com.soaproject.maintenance.dto;

import com.soaproject.maintenance.entity.AlertMetricType;
import com.soaproject.maintenance.entity.AlertSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateAlertRequest(
        @NotBlank @Size(max = 50) String machineId,
        @NotNull AlertMetricType metricType,
        @NotNull AlertSeverity severity,
        @NotNull BigDecimal observedValue,
        @NotNull BigDecimal thresholdValue,
        @NotBlank @Size(max = 500) String message,
        @NotNull Instant occurredAt,
        @Size(max = 100) String sourceEventId) {}