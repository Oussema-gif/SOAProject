package com.soaproject.maintenance.dto;

import com.soaproject.maintenance.entity.ReservationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReservationAssociationRequest(
        @NotBlank @Size(max = 64) String reservationId,
        @NotBlank @Size(max = 32) String partId,
        @NotNull @Min(1) Integer quantity,
        ReservationStatus status) {}