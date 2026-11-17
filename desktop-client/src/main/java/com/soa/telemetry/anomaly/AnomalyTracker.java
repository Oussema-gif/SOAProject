package com.soa.telemetry.anomaly;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class AnomalyTracker {

    private final Thresholds thresholds;

    /** Per metric: last severity we emitted an alert for. NORMAL = no active alert. */
    private final Map<MetricType, Severity> lastReported = new EnumMap<>(MetricType.class);

    public AnomalyTracker(Thresholds thresholds) {
        this.thresholds = thresholds;
        for (MetricType m : MetricType.values()) {
            lastReported.put(m, Severity.NORMAL);
        }
    }

    /**
     * Returns a decision for the FIRST metric that just escalated to a new
     * severity level. Returns empty if nothing new (dedup) or if everything
     * returned to NORMAL.
     */
    public Optional<AnomalyDecision> evaluate(
            String machineId,
            double temperatureC,
            double vibrationMmS,
            double pressureBar,
            double energyKw,
            Instant at) {

        Optional<AnomalyDecision> d = check(machineId, MetricType.TEMPERATURE, temperatureC, at);
        if (d.isPresent()) return d;
        d = check(machineId, MetricType.VIBRATION, vibrationMmS, at);
        if (d.isPresent()) return d;
        d = check(machineId, MetricType.PRESSURE, pressureBar, at);
        if (d.isPresent()) return d;
        return check(machineId, MetricType.ENERGY, energyKw, at);
    }

    private Optional<AnomalyDecision> check(
            String machineId, MetricType metric, double value, Instant at) {

        Severity current  = thresholds.classify(value, metric);
        Severity previous = lastReported.get(metric);

        if (current == Severity.NORMAL) {
            lastReported.put(metric, Severity.NORMAL);   // clear state
            return Optional.empty();
        }
        if (current == previous) {
            return Optional.empty();                     // dedup — already reported this level
        }

        lastReported.put(metric, current);

        double threshold = current == Severity.CRITICAL
                ? thresholds.criticalThreshold(metric)
                : thresholds.warningThreshold(metric);

        return Optional.of(new AnomalyDecision(
                new AnomalyKey(machineId, metric),
                current,
                value,
                threshold,
                at));
    }
}
