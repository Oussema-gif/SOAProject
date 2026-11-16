package com.soaproject.maintenance.dto;

import com.soaproject.maintenance.entity.MachineStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMachineRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String location,
        @Size(max = 100) String model,
        @NotNull MachineStatus status) {}