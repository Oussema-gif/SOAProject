package com.soaproject.maintenance.dto;

import com.soaproject.maintenance.entity.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkOrderStatusRequest(@NotNull WorkOrderStatus status) {}