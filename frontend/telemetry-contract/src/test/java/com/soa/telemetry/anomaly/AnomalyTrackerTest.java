package com.soa.telemetry.anomaly;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class AnomalyTrackerTest {

    private final Thresholds t = Thresholds.defaults();
    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");

    @Test void sustainedCriticalFiresOnce() {
        var tracker = new AnomalyTracker(t);
        int alerts = 0;
        for (int i = 0; i < 10; i++) {
            if (tracker.evaluate("M1", 90, 2, 4, 8, now).isPresent()) alerts++;
        }
        assertEquals(1, alerts, "Sustained critical must not spam alerts");
    }

    @Test void recoveryAllowsNextAlert() {
        var tracker = new AnomalyTracker(t);
        assertTrue (tracker.evaluate("M1", 90, 2, 4, 8, now).isPresent());
        assertFalse(tracker.evaluate("M1", 90, 2, 4, 8, now).isPresent());
        assertFalse(tracker.evaluate("M1", 55, 2, 4, 8, now).isPresent());
        assertTrue (tracker.evaluate("M1", 90, 2, 4, 8, now).isPresent());
    }

    @Test void escalationFiresNewAlert() {
        var tracker = new AnomalyTracker(t);
        assertTrue (tracker.evaluate("M1", 80, 2, 4, 8, now).isPresent());
        assertFalse(tracker.evaluate("M1", 82, 2, 4, 8, now).isPresent());
        assertTrue (tracker.evaluate("M1", 90, 2, 4, 8, now).isPresent());
    }

    @Test void differentMachinesAreIndependent() {
        var tracker = new AnomalyTracker(t);
        assertTrue(tracker.evaluate("M1", 90, 2, 4, 8, now).isPresent());
        assertTrue(tracker.evaluate("M2", 90, 2, 4, 8, now).isPresent());
        assertEquals(2, tracker.activeCount());
    }

    @Test void differentMetricsAreIndependent() {
        var tracker = new AnomalyTracker(t);
        assertTrue (tracker.evaluate("M1", 90, 8, 4, 8, now).isPresent());
        assertTrue (tracker.evaluate("M1", 90, 8, 4, 8, now).isPresent());
        assertFalse(tracker.evaluate("M1", 90, 8, 4, 8, now).isPresent());
    }
}