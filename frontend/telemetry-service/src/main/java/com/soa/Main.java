package com.soa.telemetry;

import com.soa.telemetry.anomaly.Thresholds;
import com.soa.telemetry.grpc.TelemetryServiceImpl;
import com.soa.telemetry.simulator.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        var sim = new MachineSimulator(List.of("MACHINE-001", "MACHINE-002", "MACHINE-003"));
        sim.start();

        Server server = ServerBuilder.forPort(9090)
                .addService(new TelemetryServiceImpl(sim, Thresholds.fromSystemProperties()))
                .build().start();
        System.out.println("gRPC telemetry listening on :9090");
        System.out.println("Commands: scenario <MACHINE_ID> <NORMAL|HIGH_TEMPERATURE|HIGH_VIBRATION|PRESSURE_ANOMALY|OFFLINE|RECOVERY> | start | stop | quit");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> { sim.close(); server.shutdown(); }));

        var in = new Scanner(System.in);
        while (in.hasNextLine()) {
            String[] p = in.nextLine().trim().split("\\s+");
            try {
                switch (p[0]) {
                    case "scenario" -> sim.setScenario(p[1], Scenario.valueOf(p[2]));
                    case "start" -> sim.start();
                    case "stop" -> sim.stop();
                    case "quit" -> { server.shutdown(); return; }
                    default -> System.out.println("unknown command");
                }
            } catch (Exception e) { System.out.println("error: " + e.getMessage()); }
        }
        server.awaitTermination();
    }
}