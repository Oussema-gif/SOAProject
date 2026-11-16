package com.soaproject.maintenance.dto;

import com.soaproject.maintenance.entity.WorkOrderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkOrderRequest(
        @NotBlank @Size(max = 50) String machineId,
        UUID alertId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        WorkOrderPriority priority) {}