package com.soaproject.maintenance.controller;

import com.soaproject.maintenance.dto.AlertResponse;
import com.soaproject.maintenance.dto.CreateAlertRequest;
import com.soaproject.maintenance.dto.PageResponse;
import com.soaproject.maintenance.entity.AlertSeverity;
import com.soaproject.maintenance.entity.AlertStatus;
import com.soaproject.maintenance.service.AlertCreateResult;
import com.soaproject.maintenance.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AlertResponse> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.list(status, machineId, severity, page, size));
    }

    @GetMapping("/{id}")
    public AlertResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public ResponseEntity<AlertResponse> create(@Valid @RequestBody CreateAlertRequest req) {
        AlertCreateResult result = service.create(req);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.alert());
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public AlertResponse acknowledge(@PathVariable UUID id) {
        return service.acknowledge(id);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public AlertResponse resolve(@PathVariable UUID id) {
        return service.resolve(id);
    }
}