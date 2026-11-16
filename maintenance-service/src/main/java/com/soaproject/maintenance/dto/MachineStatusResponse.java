package com.soaproject.maintenance.dto;

public record MachineStatusResponse(
        String machineId,
        String status) {}