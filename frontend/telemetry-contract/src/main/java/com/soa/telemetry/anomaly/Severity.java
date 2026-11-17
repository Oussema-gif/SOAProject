package com.soa.telemetry.anomaly;

public enum Severity {
    NORMAL, WARNING, CRITICAL;

    public static Severity of(String s) {
        if (s == null) return NORMAL;
        return switch (s) {
            case "CRITICAL" -> CRITICAL;
            case "WARNING"  -> WARNING;
            default          -> NORMAL;
        };
    }
}