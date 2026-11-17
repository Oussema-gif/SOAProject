package com.soa.telemetry.anomaly;

public record Thresholds(
        double tempWarn, double tempCrit,
        double vibWarn,  double vibCrit,
        double presWarn, double presCrit,
        double energyWarn, double energyCrit) {

    public static Thresholds defaults() {
        return new Thresholds(75, 85, 4, 7, 6, 7, 12, 15);
    }

    public static Thresholds fromSystemProperties() {
        return new Thresholds(
                prop("threshold.temp.warn",   75),
                prop("threshold.temp.crit",   85),
                prop("threshold.vib.warn",     4),
                prop("threshold.vib.crit",     7),
                prop("threshold.pres.warn",    6),
                prop("threshold.pres.crit",    7),
                prop("threshold.energy.warn", 12),
                prop("threshold.energy.crit", 15));
    }

    private static double prop(String key, double def) {
        String v = System.getProperty(key);
        return v == null ? def : Double.parseDouble(v);
    }

    public Severity classify(double value, MetricType metric) {
        return switch (metric) {
            case TEMPERATURE -> classifyOne(value, tempWarn, tempCrit);
            case VIBRATION   -> classifyOne(value, vibWarn,  vibCrit);
            case PRESSURE    -> classifyOne(value, presWarn, presCrit);
            case ENERGY      -> classifyOne(value, energyWarn, energyCrit);
        };
    }

    private static Severity classifyOne(double v, double warn, double crit) {
        if (v > crit) return Severity.CRITICAL;
        if (v > warn) return Severity.WARNING;
        return Severity.NORMAL;
    }

    public Severity classifyAll(double t, double v, double p, double e) {
        Severity max = Severity.NORMAL;
        for (Severity s : new Severity[]{
                classify(t, MetricType.TEMPERATURE),
                classify(v, MetricType.VIBRATION),
                classify(p, MetricType.PRESSURE),
                classify(e, MetricType.ENERGY)}) {
            if (s.ordinal() > max.ordinal()) max = s;
        }
        return max;
    }
}