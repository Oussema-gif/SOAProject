package com.soaproject.maintenance.dto;

import com.soaproject.maintenance.entity.MachineStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMachineRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z0-9-]+$",
                 message = "machineId must contain only uppercase letters, digits and dashes")
        String machineId,

        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String location,
        @Size(max = 100) String model,
        MachineStatus status) {}