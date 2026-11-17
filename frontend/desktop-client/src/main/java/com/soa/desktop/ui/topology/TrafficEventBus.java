package com.soa.desktop.ui.topology;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class TrafficEventBus {

    public record TrafficEvent(String protocol, String direction, String status, Instant occurredAt) { }

    private static final CopyOnWriteArrayList<Consumer<TrafficEvent>> listeners = new CopyOnWriteArrayList<>();

    private TrafficEventBus() { }

    public static AutoCloseable subscribe(Consumer<TrafficEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public static void publish(String protocol, String direction, String status) {
        TrafficEvent event = new TrafficEvent(protocol, direction, status, Instant.now());
        for (Consumer<TrafficEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                System.err.println("Traffic event listener failed: " + exception.getMessage());
            }
        }
    }
}
