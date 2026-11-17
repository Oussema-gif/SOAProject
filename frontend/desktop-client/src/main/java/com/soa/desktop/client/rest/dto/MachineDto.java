package com.soa.desktop.client.rest.dto;

import java.time.Instant;

public record MachineDto(
        String id,
        String name,
        String status,
        String location,
        Instant lastSeenAt) { }