package com.soa.desktop.orchestration;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.anomaly.AnomalyTracker;
import com.soa.telemetry.anomaly.MetricType;
import com.soa.telemetry.anomaly.Severity;
import com.soa.telemetry.anomaly.Thresholds;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AnomalyWorkflowOrchestrator implements AutoCloseable {

    private final GrpcTelemetryClient grpc;
    private final AnomalyTracker tracker;
    private final Thresholds thresholds;
    private final String machineId;

    private final Consumer<TelemetryReading> onEveryReading;
    private final Consumer<AlertDecisionPayload> onNewAlert;
    private final Consumer<AlertDecisionPayload> onCleared;
    private final Consumer<Throwable> onConnectionError;

    private GrpcTelemetryClient.Cancellable activeStream;
    private final AtomicBoolean started = new AtomicBoolean(false);

    // Tracks previously-known severity per metric so we can detect
    // "this metric just returned to NORMAL" and fire onCleared.
    private final Severity[] lastSeverity = new Severity[MetricType.values().length];

    public AnomalyWorkflowOrchestrator(
            GrpcTelemetryClient grpc,
            Thresholds thresholds,
            String machineId,
            Consumer<TelemetryReading> onEveryReading,
            Consumer<AlertDecisionPayload> onNewAlert,
            Consumer<AlertDecisionPayload> onCleared,
            Consumer<Throwable> onConnectionError) {
        this.grpc = grpc;
        this.thresholds = thresholds;
        this.tracker = new AnomalyTracker(thresholds);
        this.machineId = machineId;
        this.onEveryReading = onEveryReading;
        this.onNewAlert = onNewAlert;
        this.onCleared = onCleared;
        this.onConnectionError = onConnectionError;
        java.util.Arrays.fill(lastSeverity, Severity.NORMAL);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) return;
        activeStream = grpc.stream(machineId, this::handleReading, this::handleError);
    }

    private void handleReading(TelemetryReading r) {
        try { onEveryReading.accept(r); } catch (Exception ignored) { }

        Instant at = Instant.ofEpochMilli(r.getTimestampEpochMs());

        // First: detect clears BEFORE we ask the tracker for new alerts,
        // because the tracker clears its state when it sees NORMAL.
        checkForClears(r, at);

        // Second: ask the tracker if a NEW alert should fire.
        tracker.evaluate(
                machineId,
                r.getTemperatureC(),
                r.getVibrationMmS(),
                r.getPressureBar(),
                r.getEnergyKw(),
                at
        ).ifPresent(decision -> {
            var payload = new AlertDecisionPayload(
                    decision.key().machineId(),
                    decision.key().metric(),
                    decision.severity(),
                    decision.observedValue(),
                    decision.thresholdValue(),
                    decision.occurredAt());
            try { onNewAlert.accept(payload); }
            catch (Exception ignored) { /* never let callback crash the stream */ }
        });
    }

    private void checkForClears(TelemetryReading r, Instant at) {
        checkClear(MetricType.TEMPERATURE, r.getTemperatureC(), at);
        checkClear(MetricType.VIBRATION,   r.getVibrationMmS(), at);
        checkClear(MetricType.PRESSURE,    r.getPressureBar(),  at);
        checkClear(MetricType.ENERGY,      r.getEnergyKw(),     at);
    }

    private void checkClear(MetricType metric, double value, Instant at) {
        Severity current = thresholds.classify(value, metric);
        Severity previous = lastSeverity[metric.ordinal()];

        if (previous != Severity.NORMAL && current == Severity.NORMAL) {
            // This metric just returned to NORMAL — emit a synthetic clear payload
            var payload = new AlertDecisionPayload(
                    machineId, metric, previous, value, 0.0, at);
            try { onCleared.accept(payload); }
            catch (Exception ignored) { }
        }
        lastSeverity[metric.ordinal()] = current;
    }

    private void handleError(Throwable t) {
        try { onConnectionError.accept(t); }
        catch (Exception ignored) { }
    }

    @Override
    public void close() {
        if (activeStream != null) {
            try { activeStream.cancel(); }
            catch (Exception ignored) { }
            activeStream = null;
        }
    }
}