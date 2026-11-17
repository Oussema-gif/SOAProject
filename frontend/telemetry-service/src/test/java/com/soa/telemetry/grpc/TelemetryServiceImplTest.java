package com.soa.telemetry.grpc;

import com.soa.telemetry.BatchResult;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.anomaly.Thresholds;
import com.soa.telemetry.simulator.MachineSimulator;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryServiceImplTest {

    @Test
    void batchPublishesValidReadingsAndRejectsInvalidOnes() {
        var simulator = new MachineSimulator(List.of("MACHINE-001"));
        var service = new TelemetryServiceImpl(simulator, Thresholds.defaults());
        var published = new ArrayList<TelemetryReading>();
        simulator.addListener(published::add);
        var result = new AtomicReference<BatchResult>();
        StreamObserver<BatchResult> response = new StreamObserver<>() {
            @Override
            public void onNext(BatchResult value) {
                result.set(value);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }

            @Override
            public void onCompleted() { }
        };

        var batch = service.sendTelemetryBatch(response);
        batch.onNext(reading("MACHINE-001", 82, 2, 4, 8, 1_000));
        batch.onNext(reading("MACHINE-001", Double.NaN, 2, 4, 8, 2_000));
        batch.onNext(reading("UNKNOWN", 55, 2, 4, 8, 3_000));
        batch.onCompleted();

        assertEquals(1, result.get().getAcceptedCount());
        assertEquals(2, result.get().getRejectedCount());
        assertEquals(1, published.size());
        assertEquals(82, simulator.latest("MACHINE-001").orElseThrow().getTemperatureC());
    }

    private static TelemetryReading reading(
            String machineId, double temperature, double vibration,
            double pressure, double energy, long timestamp) {
        return TelemetryReading.newBuilder()
                .setMachineId(machineId)
                .setTemperatureC(temperature)
                .setVibrationMmS(vibration)
                .setPressureBar(pressure)
                .setEnergyKw(energy)
                .setTimestampEpochMs(timestamp)
                .build();
    }
}
