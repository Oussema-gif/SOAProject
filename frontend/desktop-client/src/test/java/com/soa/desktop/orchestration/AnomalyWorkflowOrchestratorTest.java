package com.soa.desktop.orchestration;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.anomaly.MetricType;
import com.soa.telemetry.anomaly.Severity;
import com.soa.telemetry.anomaly.Thresholds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnomalyWorkflowOrchestratorTest {

    // Fake stream we control from the test — replaces the real gRPC one
    private Map<String, Consumer<TelemetryReading>> streamFeeds;
    private GrpcTelemetryClient fakeGrpc;
    private Thresholds thresholds;

    private List<AlertDecisionPayload> newAlerts;
    private List<AlertDecisionPayload> clearedAlerts;
    private List<Throwable> errors;
    private int readingCount;

    @BeforeEach
    void setUp() {
        thresholds = Thresholds.defaults();
        newAlerts = new ArrayList<>();
        clearedAlerts = new ArrayList<>();
        errors = new ArrayList<>();
        readingCount = 0;
        streamFeeds = new HashMap<>();

        // Mock GrpcTelemetryClient so it captures the onReading callback
        // instead of opening a real network connection.
        fakeGrpc = mock(GrpcTelemetryClient.class);
        when(fakeGrpc.stream(anyString(), any(), any())).thenAnswer(inv -> {
            streamFeeds.put(inv.getArgument(0), inv.getArgument(1));
            return (GrpcTelemetryClient.Cancellable) () -> { /* no-op */ };
        });
    }

    private AnomalyWorkflowOrchestrator newOrchestrator(String machineId) {
        return new AnomalyWorkflowOrchestrator(
                fakeGrpc,
                thresholds,
                machineId,
                r -> readingCount++,
                newAlerts::add,
                clearedAlerts::add,
                errors::add);
    }

    private static TelemetryReading reading(
            String machineId, double t, double v, double p, double e, long ms) {
        return TelemetryReading.newBuilder()
                .setMachineId(machineId)
                .setTemperatureC(t)
                .setVibrationMmS(v)
                .setPressureBar(p)
                .setEnergyKw(e)
                .setTimestampEpochMs(ms)
                .build();
    }

    // -----------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------

    @Test
    void normalReadings_emitNoAlerts() {
        var orch = newOrchestrator("M1");
        orch.start();

        streamFeeds.get("M1").accept(reading("M1", 55, 2, 4, 8, 1000));
        streamFeeds.get("M1").accept(reading("M1", 60, 2, 4, 8, 2000));
        streamFeeds.get("M1").accept(reading("M1", 65, 2, 4, 8, 3000));

        assertEquals(3, readingCount, "onEveryReading must fire per reading");
        assertTrue(newAlerts.isEmpty(), "no alerts expected for normal readings");
        assertTrue(clearedAlerts.isEmpty());
    }

    @Test
    void sustainedCritical_emitsOneAlert() {
        var orch = newOrchestrator("M1");
        orch.start();

        for (int i = 0; i < 10; i++) {
            streamFeeds.get("M1").accept(reading("M1", 90, 2, 4, 8, 1000L + i * 1000));
        }

        assertEquals(10, readingCount);
        assertEquals(1, newAlerts.size(), "deduplication: 10 criticals → 1 alert");
        assertEquals(MetricType.TEMPERATURE, newAlerts.get(0).metric());
        assertEquals(Severity.CRITICAL, newAlerts.get(0).severity());
        assertEquals("M1", newAlerts.get(0).machineId());
    }

    @Test
    void warningThenCritical_emitsEscalation() {
        var orch = newOrchestrator("M1");
        orch.start();

        streamFeeds.get("M1").accept(reading("M1", 80, 2, 4, 8, 1000));   // WARNING
        streamFeeds.get("M1").accept(reading("M1", 82, 2, 4, 8, 2000));   // still WARNING — deduped
        streamFeeds.get("M1").accept(reading("M1", 90, 2, 4, 8, 3000));   // CRITICAL — escalation

        assertEquals(2, newAlerts.size());
        assertEquals(Severity.WARNING,  newAlerts.get(0).severity());
        assertEquals(Severity.CRITICAL, newAlerts.get(1).severity());
    }

    @Test
    void recovery_emitsClear_andAllowsNextAlert() {
        var orch = newOrchestrator("M1");
        orch.start();

        streamFeeds.get("M1").accept(reading("M1", 90, 2, 4, 8, 1000));   // CRITICAL alert
        streamFeeds.get("M1").accept(reading("M1", 55, 2, 4, 8, 2000));   // back to normal → clear
        streamFeeds.get("M1").accept(reading("M1", 90, 2, 4, 8, 3000));   // CRITICAL alert again

        assertEquals(2, newAlerts.size(), "alert again after recovery");
        assertEquals(1, clearedAlerts.size(), "one clear on recovery");
        assertEquals(MetricType.TEMPERATURE, clearedAlerts.get(0).metric());
        assertEquals(Severity.CRITICAL, clearedAlerts.get(0).severity());
    }

    @Test
    void differentMachines_independentAlerts() {
        // Orchestrators are per-machine; two of them track independently.
        var orch1 = newOrchestrator("M1");
        var orch2 = newOrchestrator("M2");
        orch1.start();
        orch2.start();

        streamFeeds.get("M1").accept(reading("M1", 90, 2, 4, 8, 1000));
        streamFeeds.get("M2").accept(reading("M2", 55, 2, 4, 8, 1000));

        assertEquals(1, newAlerts.size());
        assertEquals("M1", newAlerts.get(0).machineId());
        assertEquals(2, readingCount);
    }

    @Test
    void start_isIdempotent() {
        var orch = newOrchestrator("M1");
        orch.start();
        orch.start();
        orch.start();
        // The mock's stream() should only be called once
        verify(fakeGrpc, times(1)).stream(anyString(), any(), any());
    }
}