package com.soa.desktop.orchestration;

import com.soa.telemetry.anomaly.MetricType;
import com.soa.telemetry.anomaly.Severity;

import java.time.Instant;

/**
 * Plain payload emitted by the orchestrator when a NEW anomaly must become
 * an alert. Block 6 will serialize this to the REST createAlert body.
 */
public record AlertDecisionPayload(
        String machineId,
        MetricType metric,
        Severity severity,
        double observedValue,
        double thresholdValue,
        Instant occurredAt) { }