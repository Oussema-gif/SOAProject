package com.soa.telemetry.simulator;

import java.util.Random;

public class MachineState {
    final String id;
    final Random rng;
    volatile Scenario scenario = Scenario.NORMAL;
    double temp = 55, vib = 2.0, pres = 4.0, energy = 8.0;

    MachineState(String id, long seed) { this.id = id; this.rng = new Random(seed); }

    private static double toward(double cur, double target, double step) {
        return cur + Math.max(-step, Math.min(step, target - cur));
    }

    /** Advance one tick; deterministic for a given seed and scenario sequence. */
    void advance() {
        double tT = 55, tV = 2.0, tP = 4.0, tE = 8.0, sT = 2, sV = 0.3, sP = 0.2, sE = 0.4;
        switch (scenario) {
            case HIGH_TEMPERATURE -> { tT = 95; sT = 1.5; }
            case HIGH_VIBRATION   -> { tV = 8.5; sV = 0.4; }
            case PRESSURE_ANOMALY -> { tP = 7.8; sP = 0.25; }
            default -> { }
        }
        temp   = toward(temp, tT, sT)   + (rng.nextDouble() - 0.5) * 0.6;
        vib    = toward(vib, tV, sV)    + (rng.nextDouble() - 0.5) * 0.1;
        pres   = toward(pres, tP, sP)   + (rng.nextDouble() - 0.5) * 0.05;
        energy = toward(energy, tE, sE) + (rng.nextDouble() - 0.5) * 0.2;
    }
}