package com.soaproject.maintenance.entity;

import java.util.EnumSet;
import java.util.Set;

public enum WorkOrderStatus {
    OPEN,
    CHECKING_INVENTORY,
    READY_FOR_MAINTENANCE,
    WAITING_FOR_PARTS,
    PENDING_RESERVATION,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public Set<WorkOrderStatus> allowedNext() {
        return switch (this) {
            case OPEN -> EnumSet.of(CHECKING_INVENTORY, CANCELLED);
            case CHECKING_INVENTORY -> EnumSet.of(
                    PENDING_RESERVATION, WAITING_FOR_PARTS, READY_FOR_MAINTENANCE, CANCELLED);
            case PENDING_RESERVATION -> EnumSet.of(
                    READY_FOR_MAINTENANCE, WAITING_FOR_PARTS, CANCELLED);
            case WAITING_FOR_PARTS -> EnumSet.of(CHECKING_INVENTORY, CANCELLED);
            case READY_FOR_MAINTENANCE -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(WorkOrderStatus.class);
        };
    }

    public boolean canTransitionTo(WorkOrderStatus next) {
        return next != null && allowedNext().contains(next);
    }
}