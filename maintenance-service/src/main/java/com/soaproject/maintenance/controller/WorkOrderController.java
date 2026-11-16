package com.soaproject.maintenance.controller;

import com.soaproject.maintenance.dto.AssignTechnicianRequest;
import com.soaproject.maintenance.dto.CreateWorkOrderRequest;
import com.soaproject.maintenance.dto.PageResponse;
import com.soaproject.maintenance.dto.ReservationAssociationRequest;
import com.soaproject.maintenance.dto.UpdateWorkOrderStatusRequest;
import com.soaproject.maintenance.dto.WorkOrderResponse;
import com.soaproject.maintenance.entity.WorkOrderStatus;
import com.soaproject.maintenance.service.WorkOrderCreateResult;
import com.soaproject.maintenance.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/work-orders")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<WorkOrderResponse> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) UUID technicianId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.list(status, machineId, technicianId, page, size));
    }

    @GetMapping("/{id}")
    public WorkOrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest req) {
        WorkOrderCreateResult result = service.create(req);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.workOrder());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR','TECHNICIAN')")
    public WorkOrderResponse updateStatus(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateWorkOrderStatusRequest req,
                                          Authentication authentication) {
        return service.updateStatus(id, req.status(), authentication);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public WorkOrderResponse assign(@PathVariable UUID id,
                                    @Valid @RequestBody AssignTechnicianRequest req) {
        return service.assign(id, req);
    }

    @PatchMapping("/{id}/reservation")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public WorkOrderResponse reservation(@PathVariable UUID id,
                                         @Valid @RequestBody ReservationAssociationRequest req) {
        return service.attachReservation(id, req);
    }
}