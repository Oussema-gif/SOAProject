package com.soa.telemetry.anomaly;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-metric warning / critical thresholds.
 * Defaults match the project Simulation section:
 *   Temperature  > 75 WARNING, > 85 CRITICAL  (°C)
 *   Vibration    >  4 WARNING, >  7 CRITICAL  (mm/s)
 *   Pressure     >  6 WARNING, >  7 CRITICAL  (bar)
 *   Energy       > 12 WARNING, > 15 CRITICAL  (kW)
 */
public final class Thresholds {

    public static final class Limit {
        public final double warning;
        public final double critical;
        public Limit(double warning, double critical) {
            this.warning = warning;
            this.critical = critical;
        }
    }

    private final Map<MetricType, Limit> limits = new EnumMap<>(MetricType.class);

    public Thresholds() {
        limits.put(MetricType.TEMPERATURE, new Limit(75.0, 85.0));
        limits.put(MetricType.VIBRATION,   new Limit(4.0,  7.0));
        limits.put(MetricType.PRESSURE,    new Limit(6.0,  7.0));
        limits.put(MetricType.ENERGY,      new Limit(12.0, 15.0));
    }

    public static Thresholds defaults() {
        return new Thresholds();
    }

    public Severity classify(double value, MetricType metric) {
        Limit l = limits.get(metric);
        if (l == null) return Severity.NORMAL;
        if (value > l.critical) return Severity.CRITICAL;
        if (value > l.warning)  return Severity.WARNING;
        return Severity.NORMAL;
    }

    public double warningThreshold(MetricType metric) {
        Limit l = limits.get(metric);
        return l == null ? 0.0 : l.warning;
    }

    public double criticalThreshold(MetricType metric) {
        Limit l = limits.get(metric);
        return l == null ? 0.0 : l.critical;
    }
}
