package com.soaproject.maintenance.service;

import com.soaproject.maintenance.dto.WorkOrderResponse;

public record WorkOrderCreateResult(WorkOrderResponse workOrder, boolean created) {}