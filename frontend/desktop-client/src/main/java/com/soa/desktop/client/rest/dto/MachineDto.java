package com.soa.desktop.client.rest.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;

public record MachineDto(
        @JsonAlias("machineId")
        String id,
        String name,
        String status,
        String location,
        Instant lastSeenAt) { }