package com.soaproject.maintenance.controller;

import com.soaproject.maintenance.dto.CreateMachineRequest;
import com.soaproject.maintenance.dto.MachineResponse;
import com.soaproject.maintenance.dto.MachineStatusResponse;
import com.soaproject.maintenance.dto.PageResponse;
import com.soaproject.maintenance.dto.UpdateMachineRequest;
import com.soaproject.maintenance.entity.MachineStatus;
import com.soaproject.maintenance.service.MachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/machines")
public class MachineController {

    private final MachineService service;

    public MachineController(MachineService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<MachineResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) MachineStatus status,
            @RequestParam(required = false) String q) {
        return PageResponse.from(service.list(page, size, status, q));
    }

    @GetMapping("/{machineId}")
    public MachineResponse get(@PathVariable String machineId) {
        return service.get(machineId);
    }

    @GetMapping("/{machineId}/status")
    public MachineStatusResponse status(@PathVariable String machineId) {
        return new MachineStatusResponse(machineId, service.getStatus(machineId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public ResponseEntity<MachineResponse> create(@Valid @RequestBody CreateMachineRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{machineId}")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    public MachineResponse update(@PathVariable String machineId,
                                  @Valid @RequestBody UpdateMachineRequest req) {
        return service.update(machineId, req);
    }

    @DeleteMapping("/{machineId}")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE_OPERATOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String machineId) {
        service.delete(machineId);
    }
}