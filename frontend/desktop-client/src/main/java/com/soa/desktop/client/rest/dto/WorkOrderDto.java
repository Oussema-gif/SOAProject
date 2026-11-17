package com.soa.desktop.client.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkOrderDto(
        String id,
        String machineId,
        String alertId,
        String title,
        String description,
        String status,
        String priority,
        String assignedTechnicianId,
        String reservationReference,
        Instant createdAt) { }