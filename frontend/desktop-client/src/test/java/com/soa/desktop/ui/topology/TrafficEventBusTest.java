package com.soa.desktop.ui.topology;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrafficEventBusTest {

    @Test
    void publishesProtocolDirectionAndStatusAndRemovesClosedListeners() throws Exception {
        AtomicReference<TrafficEventBus.TrafficEvent> received = new AtomicReference<>();
        AutoCloseable subscription = TrafficEventBus.subscribe(received::set);

        TrafficEventBus.publish("REST", "REQUEST", "PENDING");
        assertNotNull(received.get());
        assertEquals("REST", received.get().protocol());
        assertEquals("REQUEST", received.get().direction());
        assertEquals("PENDING", received.get().status());
        assertNotNull(received.get().occurredAt());

        subscription.close();
        received.set(null);
        TrafficEventBus.publish("SOAP", "RESPONSE", "SUCCESS");
        assertNull(received.get());
    }

    @Test
    void publishesLiveTelemetryStateUpdates() {
        AtomicReference<TrafficEventBus.TrafficEvent> received = new AtomicReference<>();
        AutoCloseable subscription = TrafficEventBus.subscribe(received::set);
        try {
            TrafficEventBus.publish("gRPC", "STATE", "CRITICAL");

            assertNotNull(received.get());
            assertEquals("gRPC", received.get().protocol());
            assertEquals("STATE", received.get().direction());
            assertEquals("CRITICAL", received.get().status());
        } finally {
            try {
                subscription.close();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    @Test
    void listenerFailureDoesNotChangeClientCallBehavior() {
        AutoCloseable subscription = TrafficEventBus.subscribe(event -> {
            throw new IllegalStateException("observer failure");
        });
        try {
            assertDoesNotThrow(() -> TrafficEventBus.publish("gRPC", "RESPONSE", "FAILED"));
        } finally {
            try {
                subscription.close();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
