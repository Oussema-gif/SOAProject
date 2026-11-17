package com.soa.desktop.client.rest.dto;

public record CreateWorkOrderRequest(
        String machineId,
        String alertId,
        String title,
        String description,
        String priority) { }