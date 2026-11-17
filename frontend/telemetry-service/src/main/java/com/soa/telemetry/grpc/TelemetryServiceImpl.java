package com.soa.telemetry.grpc;

import com.soa.telemetry.BatchResult;
import com.soa.telemetry.MachineRequest;
import com.soa.telemetry.MonitorRequest;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.TelemetryServiceGrpc;
import com.soa.telemetry.TelemetrySnapshot;
import com.soa.telemetry.anomaly.Thresholds;
import com.soa.telemetry.simulator.MachineSimulator;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.HashSet;
import java.util.function.Consumer;

public class TelemetryServiceImpl extends TelemetryServiceGrpc.TelemetryServiceImplBase {

    private final MachineSimulator sim;
    private final Thresholds thresholds;

    public TelemetryServiceImpl(MachineSimulator sim, Thresholds thresholds) {
        this.sim = sim;
        this.thresholds = thresholds;
    }

    private static StatusRuntimeException unknown(String id) {
        return Status.NOT_FOUND.withDescription("Unknown machine: " + id).asRuntimeException();
    }

    @Override
    public void getMachineSnapshot(MachineRequest req,
                                   StreamObserver<TelemetrySnapshot> out) {
        String id = req.getMachineId();
        if (id.isBlank()) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("machine_id required")
                    .asRuntimeException());
            return;
        }
        if (!sim.exists(id)) {
            out.onError(unknown(id));
            return;
        }
        var r = sim.latest(id).orElseThrow();
        String status = sim.isOffline(id)
                ? "OFFLINE"
                : thresholds.classifyAll(
                r.getTemperatureC(), r.getVibrationMmS(),
                r.getPressureBar(), r.getEnergyKw()).name();

        out.onNext(TelemetrySnapshot.newBuilder()
                .setMachineId(id)
                .setTemperatureC(r.getTemperatureC())
                .setVibrationMmS(r.getVibrationMmS())
                .setPressureBar(r.getPressureBar())
                .setEnergyKw(r.getEnergyKw())
                .setTimestampEpochMs(r.getTimestampEpochMs())
                .setMachineStatus(status)
                .build());
        out.onCompleted();
    }

    @Override
    public void streamMachineTelemetry(MachineRequest req,
                                       StreamObserver<TelemetryReading> out) {
        String id = req.getMachineId();
        if (id.isBlank()) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("machine_id required")
                    .asRuntimeException());
            return;
        }
        if (!sim.exists(id)) {
            out.onError(unknown(id));
            return;
        }

        var server = (ServerCallStreamObserver<TelemetryReading>) out;
        final Consumer<TelemetryReading>[] holder = new Consumer[1];

        Consumer<TelemetryReading> listener = r -> {
            if (server.isCancelled()) return;
            if (!r.getMachineId().equals(id)) return;
            try {
                server.onNext(r);
            } catch (StatusRuntimeException e) {
                // transport gone — stop feeding this subscriber
                sim.removeListener(holder[0]);
            }
        };
        holder[0] = listener;

        server.setOnCancelHandler(() -> sim.removeListener(listener));
        sim.addListener(listener);
    }

    @Override
    public StreamObserver<TelemetryReading> sendTelemetryBatch(
            StreamObserver<BatchResult> out) {
        return new StreamObserver<>() {
            int ok, bad;

            @Override
            public void onNext(TelemetryReading r) {
                if (sim.exists(r.getMachineId())) ok++;
                else bad++;
            }

            @Override
            public void onError(Throwable t) {
                // client aborted — nothing to do
            }

            @Override
            public void onCompleted() {
                out.onNext(BatchResult.newBuilder()
                        .setAcceptedCount(ok)
                        .setRejectedCount(bad)
                        .setMessage("accepted=" + ok + ", rejected=" + bad)
                        .build());
                out.onCompleted();
            }
        };
    }

    @Override
    public void monitorMachines(MonitorRequest req,
                                StreamObserver<TelemetryReading> out) {
        var ids = new HashSet<>(req.getMachineIdsList());
        if (ids.isEmpty()) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("machine_ids must not be empty")
                    .asRuntimeException());
            return;
        }
        for (String id : ids) {
            if (!sim.exists(id)) {
                out.onError(unknown(id));
                return;
            }
        }

        var server = (ServerCallStreamObserver<TelemetryReading>) out;
        final Consumer<TelemetryReading>[] holder = new Consumer[1];

        Consumer<TelemetryReading> listener = r -> {
            if (server.isCancelled()) return;
            if (!ids.contains(r.getMachineId())) return;
            try {
                server.onNext(r);
            } catch (StatusRuntimeException e) {
                sim.removeListener(holder[0]);
            }
        };
        holder[0] = listener;

        server.setOnCancelHandler(() -> sim.removeListener(listener));
        sim.addListener(listener);
    }
}