package com.soa.desktop.client.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record CreateAlertRequest(
        String machineId,
        String metricType,
        String severity,
        double observedValue,
        double thresholdValue,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,
        String sourceEventId) { }