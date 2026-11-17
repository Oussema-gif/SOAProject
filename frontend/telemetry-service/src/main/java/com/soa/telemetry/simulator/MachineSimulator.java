package com.soa.telemetry.simulator;

import com.soa.telemetry.TelemetryReading;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MachineSimulator implements AutoCloseable {
    private final Map<String, MachineState> machines = new ConcurrentHashMap<>();
    private final Map<String, TelemetryReading> latest = new ConcurrentHashMap<>();
    private final Set<Consumer<TelemetryReading>> listeners = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> { var t = new Thread(r, "simulator"); t.setDaemon(true); return t; });
    private ScheduledFuture<?> task;

    public MachineSimulator(List<String> machineIds) {
        long seed = 42;
        for (String id : machineIds) {
            var s = new MachineState(id, seed++);
            machines.put(id, s);
            latest.put(id, reading(s));
        }
    }

    public boolean exists(String id) { return machines.containsKey(id); }
    public Optional<TelemetryReading> latest(String id) { return Optional.ofNullable(latest.get(id)); }
    public boolean isOffline(String id) { var s = machines.get(id); return s != null && s.scenario == Scenario.OFFLINE; }
    public void addListener(Consumer<TelemetryReading> l) { listeners.add(l); }
    public void removeListener(Consumer<TelemetryReading> l) { listeners.remove(l); }

    public void setScenario(String id, Scenario sc) {
        var s = machines.get(id);
        if (s == null) throw new IllegalArgumentException("Unknown machine " + id);
        s.scenario = sc;
    }

    public synchronized void start() {
        if (task != null && !task.isCancelled()) return;
        task = scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (task != null) { task.cancel(false); task = null; }
    }

    private void tick() {
        for (var s : machines.values()) {
            if (s.scenario == Scenario.OFFLINE) continue;          // no reading emitted
            s.advance();
            var r = reading(s);
            latest.put(s.id, r);
            for (var l : listeners) {
                try { l.accept(r); } catch (Exception e) { listeners.remove(l); }
            }
        }
    }

    private static TelemetryReading reading(MachineState s) {
        return TelemetryReading.newBuilder().setMachineId(s.id)
                .setTemperatureC(s.temp).setVibrationMmS(s.vib)
                .setPressureBar(s.pres).setEnergyKw(s.energy)
                .setTimestampEpochMs(System.currentTimeMillis()).build();
    }

    @Override public void close() { scheduler.shutdownNow(); }
}