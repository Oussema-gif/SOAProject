package com.soa.telemetry.anomaly;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AnomalyTracker {

    public record AnomalyKey(String machineId, MetricType metric) {
        public AnomalyKey {
            Objects.requireNonNull(machineId);
            Objects.requireNonNull(metric);
        }
    }

    public record AlertDecision(
            AnomalyKey key,
            Severity severity,
            double observedValue,
            double thresholdValue,
            Instant occurredAt) { }

    private final Thresholds thresholds;
    private final Map<AnomalyKey, Severity> active = new ConcurrentHashMap<>();

    public AnomalyTracker(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public Optional<AlertDecision> evaluate(
            String machineId,
            double temperature, double vibration, double pressure, double energy,
            Instant at) {

        Optional<AlertDecision> hit;
        hit = check(machineId, MetricType.TEMPERATURE, temperature,
                thresholds.tempWarn(), thresholds.tempCrit(), at);
        if (hit.isPresent()) return hit;

        hit = check(machineId, MetricType.VIBRATION, vibration,
                thresholds.vibWarn(), thresholds.vibCrit(), at);
        if (hit.isPresent()) return hit;

        hit = check(machineId, MetricType.PRESSURE, pressure,
                thresholds.presWarn(), thresholds.presCrit(), at);
        if (hit.isPresent()) return hit;

        return check(machineId, MetricType.ENERGY, energy,
                thresholds.energyWarn(), thresholds.energyCrit(), at);
    }

    private Optional<AlertDecision> check(
            String machineId, MetricType metric, double value,
            double warn, double crit, Instant at) {

        AnomalyKey key = new AnomalyKey(machineId, metric);
        Severity s = thresholds.classify(value, metric);

        if (s == Severity.NORMAL) {
            active.remove(key);
            return Optional.empty();
        }

        Severity previous = active.get(key);
        if (previous == null || s.ordinal() > previous.ordinal()) {
            active.put(key, s);
            double threshold = (s == Severity.CRITICAL) ? crit : warn;
            return Optional.of(new AlertDecision(key, s, value, threshold, at));
        }
        return Optional.empty();
    }

    public int activeCount() { return active.size(); }
    public void clear()        { active.clear(); }
}