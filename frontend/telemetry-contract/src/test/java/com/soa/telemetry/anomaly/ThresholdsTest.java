package com.soa.telemetry.anomaly;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThresholdsTest {

    private final Thresholds t = Thresholds.defaults();

    @Test void temperatureBoundaries() {
        assertEquals(Severity.NORMAL,   t.classify(74.9, MetricType.TEMPERATURE));
        assertEquals(Severity.NORMAL,   t.classify(75.0, MetricType.TEMPERATURE));
        assertEquals(Severity.WARNING,  t.classify(75.1, MetricType.TEMPERATURE));
        assertEquals(Severity.WARNING,  t.classify(85.0, MetricType.TEMPERATURE));
        assertEquals(Severity.CRITICAL, t.classify(85.1, MetricType.TEMPERATURE));
    }

    @Test void vibrationBoundaries() {
        assertEquals(Severity.NORMAL,   t.classify(3.9, MetricType.VIBRATION));
        assertEquals(Severity.WARNING,  t.classify(4.1, MetricType.VIBRATION));
        assertEquals(Severity.CRITICAL, t.classify(7.1, MetricType.VIBRATION));
    }

    @Test void pressureBoundaries() {
        assertEquals(Severity.NORMAL,   t.classify(5.9, MetricType.PRESSURE));
        assertEquals(Severity.WARNING,  t.classify(6.1, MetricType.PRESSURE));
        assertEquals(Severity.CRITICAL, t.classify(7.1, MetricType.PRESSURE));
    }

    @Test void energyBoundaries() {
        assertEquals(Severity.NORMAL,   t.classify(11.9, MetricType.ENERGY));
        assertEquals(Severity.WARNING,  t.classify(12.1, MetricType.ENERGY));
        assertEquals(Severity.CRITICAL, t.classify(15.1, MetricType.ENERGY));
    }

    @Test void classifyAllPicksHighest() {
        assertEquals(Severity.NORMAL,   t.classifyAll(50, 2, 4, 8));
        assertEquals(Severity.WARNING,  t.classifyAll(80, 2, 4, 8));
        assertEquals(Severity.CRITICAL, t.classifyAll(50, 2, 4, 16));
    }
}