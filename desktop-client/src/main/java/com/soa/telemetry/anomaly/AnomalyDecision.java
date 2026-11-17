package com.soa.telemetry.anomaly;

import java.time.Instant;

public record AnomalyDecision(
        AnomalyKey key,
        Severity severity,
        double observedValue,
        double thresholdValue,
        Instant occurredAt) { }
