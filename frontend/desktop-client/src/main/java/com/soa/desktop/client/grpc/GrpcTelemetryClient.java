package com.soa.desktop.client.grpc;

import com.soa.telemetry.MachineRequest;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.TelemetryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import com.soa.desktop.ui.topology.TrafficEventBus;
import com.soa.desktop.util.ActivityLogger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;
import java.util.function.Consumer;

public class GrpcTelemetryClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final TelemetryServiceGrpc.TelemetryServiceStub asyncStub;

    public GrpcTelemetryClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = TelemetryServiceGrpc.newStub(channel);
    }

    public Cancellable stream(String machineId,
                              Consumer<TelemetryReading> onReading,
                              Consumer<Throwable> onError) {

        final ClientCallStreamObserver<MachineRequest>[] requestStream = new ClientCallStreamObserver[1];
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        AtomicBoolean firstReading = new AtomicBoolean();
        ActivityLogger.log("gRPC", "StreamMachineTelemetry", "PENDING", "Machine: " + machineId,
                requestId, null, null);

        var observer = new ClientResponseObserver<MachineRequest, TelemetryReading>() {
            @Override
            public void beforeStart(ClientCallStreamObserver<MachineRequest> rs) {
                requestStream[0] = rs;
            }

            @Override public void onNext(TelemetryReading r) {
                TrafficEventBus.publish("gRPC", "RESPONSE", "SUCCESS");
                if (firstReading.compareAndSet(false, true)) {
                    ActivityLogger.log("gRPC", "StreamMachineTelemetry", "SUCCESS",
                            "First telemetry reading received for " + machineId, requestId, null,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                }
                try { onReading.accept(r); }
                catch (Exception e) { onError.accept(e); }
            }

            @Override public void onError(Throwable t) {
                TrafficEventBus.publish("gRPC", "RESPONSE", "FAILED");
                Status status = Status.fromThrowable(t);
                ActivityLogger.log("gRPC", "StreamMachineTelemetry", "FAILED",
                        status.getCode() + ": " + status.getDescription(), requestId, null,
                        status.getCode().name(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                onError.accept(t);
            }

            @Override public void onCompleted() {
                TrafficEventBus.publish("gRPC", "RESPONSE", "SUCCESS");
            }
        };

        TrafficEventBus.publish("gRPC", "REQUEST", "PENDING");
        asyncStub.streamMachineTelemetry(
                MachineRequest.newBuilder().setMachineId(machineId).build(),
                observer);

        return () -> {
            var rs = requestStream[0];
            if (rs != null) rs.cancel("client closed stream", null);
        };
    }

    @Override
    public void close() {
        channel.shutdown();
        try { channel.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        channel.shutdownNow();
    }

    @FunctionalInterface
    public interface Cancellable { void cancel(); }
}