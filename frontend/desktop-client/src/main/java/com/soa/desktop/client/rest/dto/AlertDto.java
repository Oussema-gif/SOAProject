package com.soa.desktop.client.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertDto(
        String id,
        String machineId,
        String metricType,
        String severity,
        String status,
        double observedValue,
        double thresholdValue,
        Instant occurredAt,
        Instant createdAt,
        String message) { }