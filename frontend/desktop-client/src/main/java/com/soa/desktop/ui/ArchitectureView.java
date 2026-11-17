package com.soa.desktop.ui;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.MachineDto;
import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.ui.components.TopologyNode;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.PulseLayer;
import com.soa.desktop.ui.topology.TrafficEventBus;
import com.soa.desktop.util.ActivityLogger;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadLocalRandom;

public final class ArchitectureView extends VBox implements AutoCloseable {

    private final boolean demo;
    private final MaintenanceRestClient rest;
    private final InventorySoapClient soap;
    private final Pane routePane = new Pane();
    private final PulseLayer pulseLayer = new PulseLayer();
    private final Map<String, Path> outboundRoutes = new HashMap<>();
    private final Map<String, Path> inboundRoutes = new HashMap<>();
    private final Map<String, TopologyNode> nodes = new HashMap<>();
    private final Label serviceDetails = new Label("Select a service node to inspect its real health check and recent activity.");
    private final Timeline demoTraffic;
    private final AutoCloseable subscription;
    private GrpcTelemetryClient grpcProbe;
    private GrpcTelemetryClient.Cancellable grpcProbeStream;
    private Timeline grpcTimeout;
    private boolean closed;

    public ArchitectureView(MaintenanceRestClient rest, InventorySoapClient soap, boolean demo) {
        this.rest = rest;
        this.soap = soap;
        this.demo = demo;
        getStyleClass().add("page");
        setSpacing(20);
        setPadding(new Insets(8));

        VBox heading = new VBox(7);
        Label eyebrow = new Label("06 / NETWORK FABRIC");
        eyebrow.getStyleClass().add("section-label");
        Label title = new Label("Architecture / Live Topology");
        title.getStyleClass().add("page-title");
        Label detail = new Label("Observed service checks · PostgreSQL is reported indirectly, never assumed healthy.");
        detail.getStyleClass().add("body-muted");
        heading.getChildren().addAll(eyebrow, title, detail);

        routePane.getStyleClass().add("topology-canvas");
        routePane.setMinHeight(470);
        routePane.setPrefHeight(500);
        StackPane layers = new StackPane(routePane, pulseLayer);
        layers.getStyleClass().add("topology-frame");
        VBox.setVgrow(layers, javafx.scene.layout.Priority.ALWAYS);

        VBox legend = new VBox(6);
        Label legendTitle = new Label("FLOW LEGEND");
        legendTitle.getStyleClass().add("section-label");
        Label legendText = new Label("REST / circular packet     SOAP / capsule packet     gRPC / compact packet     red pulse / failed call");
        legendText.getStyleClass().add("mono-muted");
        legend.getChildren().addAll(legendTitle, legendText);

        VBox details = new VBox(7);
        details.getStyleClass().add("surface-card");
        Label detailsHeading = new Label("SERVICE INSPECTION");
        detailsHeading.getStyleClass().add("panel-heading");
        serviceDetails.getStyleClass().add("mono-muted");
        serviceDetails.setWrapText(true);
        details.getChildren().addAll(detailsHeading, serviceDetails);
        getChildren().addAll(heading, layers, details, legend);
        buildTopology();
        subscription = TrafficEventBus.subscribe(event -> Platform.runLater(() -> {
            Path route = "RESPONSE".equalsIgnoreCase(event.direction())
                    ? inboundRoutes.get(event.protocol().toUpperCase())
                    : outboundRoutes.get(event.protocol().toUpperCase());
            if (route != null) pulseLayer.pulse(route, event.protocol(), event.status());
            String protocol = event.protocol().equalsIgnoreCase("grpc")
                    ? "GRPC" : event.protocol().toUpperCase();
            TopologyNode node = nodes.get(protocol);
            if (!demo && node != null && "RESPONSE".equalsIgnoreCase(event.direction())) {
                if ("SUCCESS".equalsIgnoreCase(event.status())) node.setHealth("success", "LIVE");
                if ("FAILED".equalsIgnoreCase(event.status())) node.setHealth("offline", "DOWN");
            }
        }));

        if (demo) {
            demoTraffic = new Timeline(new KeyFrame(Duration.millis(520), event -> {
                String[] protocols = {"REST", "SOAP", "gRPC"};
                TrafficEventBus.publish(protocols[ThreadLocalRandom.current().nextInt(protocols.length)],
                        ThreadLocalRandom.current().nextBoolean() ? "REQUEST" : "RESPONSE", "SUCCESS");
            }));
            demoTraffic.setCycleCount(Timeline.INDEFINITE);
            demoTraffic.play();
        } else {
            demoTraffic = null;
            probeServices();
        }
    }

    private void buildTopology() {
        Path rest = connect(185, 225, 260, 100, "connection-rest");
        Path soap = connect(185, 255, 260, 355, "connection-soap");
        Path grpc = connect(185, 240, 500, 240, "connection-grpc");
        Path maintenanceDb = connect(430, 100, 780, 220, "connection-storage");
        Path inventoryDb = connect(430, 355, 780, 260, "connection-storage");
        Path telemetryDb = connect(670, 240, 780, 240, "connection-storage");

        outboundRoutes.put("REST", rest);
        outboundRoutes.put("SOAP", soap);
        outboundRoutes.put("GRPC", grpc);
        inboundRoutes.put("REST", reverse(185, 225, 260, 100));
        inboundRoutes.put("SOAP", reverse(185, 255, 260, 355));
        inboundRoutes.put("GRPC", reverse(185, 240, 500, 240));

        routePane.getChildren().addAll(rest, soap, grpc, maintenanceDb, inventoryDb, telemetryDb);
        TopologyNode desktop = new TopologyNode("NODE 01", "JavaFX Desktop", "CLIENT", "Operator console");
        TopologyNode maintenance = new TopologyNode("NODE 02", "Maintenance", "REST", "JWT · :8081");
        TopologyNode inventory = new TopologyNode("NODE 03", "Inventory", "SOAP", "API key · :8082");
        TopologyNode telemetry = new TopologyNode("NODE 04", "Telemetry", "gRPC", "Stream · :9090");
        TopologyNode database = new TopologyNode("NODE 05", "PostgreSQL", "PERSISTENCE", "Indirect observation");
        nodes.put("REST", maintenance);
        nodes.put("SOAP", inventory);
        nodes.put("GRPC", telemetry);
        nodes.put("DATABASE", database);
        nodes.put("CLIENT", desktop);
        desktop.setHealth("success", "LOCAL");
        database.setHealth("warning", "NOT PROBED");
        maintenance.setHealth("offline", demo ? "SIMULATED" : "CHECKING");
        inventory.setHealth("offline", demo ? "SIMULATED" : "CHECKING");
        telemetry.setHealth("offline", demo ? "SIMULATED" : "CHECKING");
        for (TopologyNode node : nodes.values()) {
            node.setOnMouseClicked(event -> showServiceDetails(node));
        }
        routePane.getChildren().addAll(desktop, maintenance, inventory, telemetry, database);

        double[] x = {18, 245, 245, 485, 740};
        double[] y = {190, 60, 310, 190, 190};
        desktop.relocate(x[0], y[0]);
        maintenance.relocate(x[1], y[1]);
        inventory.relocate(x[2], y[2]);
        telemetry.relocate(x[3], y[3]);
        database.relocate(x[4], y[4]);
        routePane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double extra = Math.max(0, (newWidth.doubleValue() - 980) / 2);
            desktop.setLayoutX(x[0] + extra);
            maintenance.setLayoutX(x[1] + extra);
            inventory.setLayoutX(x[2] + extra);
            telemetry.setLayoutX(x[3] + extra);
            database.setLayoutX(x[4] + extra);
        });
        Motion.stagger(routePane.getChildren());
    }

    private Path connect(double startX, double startY, double endX, double endY, String styleClass) {
        Path path = new Path(new MoveTo(startX, startY),
                new CubicCurveTo(startX + 55, startY, endX - 55, endY, endX, endY));
        path.getStyleClass().addAll("topology-connection", styleClass);
        path.setCache(true);
        path.setCacheHint(javafx.scene.CacheHint.SPEED);
        return path;
    }

    private Path reverse(double startX, double startY, double endX, double endY) {
        Path path = new Path(new MoveTo(endX, endY),
                new CubicCurveTo(endX - 55, endY, startX + 55, startY, startX, startY));
        path.getStyleClass().add("topology-connection");
        return path;
    }

    private void probeServices() {
        rest.checkHealth().whenComplete((healthy, error) -> Platform.runLater(() -> {
            if (closed) return;
            nodes.get("REST").setHealth(error == null && healthy ? "success" : "offline",
                    error == null && healthy ? "LIVE" : "DOWN");
        }));
        if (soap == null) {
            nodes.get("SOAP").setHealth("offline", "NOT CONFIGURED");
        } else {
            soap.checkAuthenticatedService().whenComplete((part, error) -> Platform.runLater(() -> {
                if (closed) return;
                nodes.get("SOAP").setHealth(error == null ? "success" : "offline",
                        error == null ? "LIVE" : "AUTH / DB ERROR");
            }));
        }
        rest.getMachines().whenComplete((machines, error) -> Platform.runLater(() -> {
            if (closed) return;
            if (error != null) {
                nodes.get("GRPC").setHealth("offline", "MACHINE LOOKUP FAILED");
            } else if (machines.isEmpty()) {
                nodes.get("GRPC").setHealth("warning", "NO MACHINE TO PROBE");
            } else {
                probeGrpc(machines.getFirst());
            }
        }));
    }

    private void probeGrpc(MachineDto machine) {
        grpcProbe = new GrpcTelemetryClient("localhost", 9090);
        long startedAt = System.nanoTime();
        AtomicReference<GrpcTelemetryClient.Cancellable> streamRef = new AtomicReference<>();
        grpcTimeout = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            if (streamRef.get() != null) streamRef.get().cancel();
            if (grpcProbe != null) grpcProbe.close();
            grpcProbe = null;
            if (!closed) nodes.get("GRPC").setHealth("offline", "NO RESPONSE · 5s");
        }));
        grpcTimeout.play();
        grpcProbeStream = grpcProbe.stream(machine.id(), reading -> Platform.runLater(() -> {
            if (closed) return;
            grpcTimeout.stop();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            nodes.get("GRPC").setHealth("success", "LIVE · " + elapsedMillis + " ms");
            ActivityLogger.log("gRPC", "StreamMachineTelemetry", "SUCCESS",
                    "First reading received for " + machine.id(), null, null, elapsedMillis);
            if (streamRef.get() != null) streamRef.get().cancel();
            if (grpcProbe != null) grpcProbe.close();
            grpcProbe = null;
        }), error -> Platform.runLater(() -> {
            if (closed) return;
            grpcTimeout.stop();
            nodes.get("GRPC").setHealth("offline", "DOWN");
            if (grpcProbe != null) grpcProbe.close();
            grpcProbe = null;
        }));
        streamRef.set(grpcProbeStream);
    }

    private void showServiceDetails(TopologyNode node) {
        String protocol = nodes.entrySet().stream().filter(entry -> entry.getValue() == node)
                .map(Map.Entry::getKey).findFirst().orElse("SERVICE");
        String endpoint = switch (protocol) {
            case "REST" -> "http://localhost:8081/actuator/health";
            case "SOAP" -> System.getenv().getOrDefault("INVENTORY_WSDL_URL",
                    "http://localhost:8082/services/inventory.wsdl") + " · authenticated getSparePart probe";
            case "GRPC" -> "localhost:9090 · first telemetry response";
            case "DATABASE" -> "No desktop database connection; persistence health is indirect.";
            default -> "Local JavaFX process";
        };
        String recent = ActivityLogger.getLogs().stream()
                .filter(entry -> protocol.equals("REST") && "REST".equalsIgnoreCase(entry.protocol())
                        || protocol.equals("SOAP") && "SOAP".equalsIgnoreCase(entry.protocol())
                        || protocol.equals("GRPC") && "gRPC".equalsIgnoreCase(entry.protocol()))
                .limit(3)
                .map(entry -> entry.time() + " · " + entry.operation() + " · " + entry.status())
                .reduce((first, second) -> first + "\n" + second).orElse("No protocol events captured.");
        String title = node.getChildren().size() > 1
                ? ((Label) node.getChildren().get(1)).getText() : "Service";
        String status = node.getChildren().size() > 4
                ? ((Label) node.getChildren().get(4)).getText() : "Unknown";
        serviceDetails.setText(title + " · " + status + "\nENDPOINT  " + endpoint + "\nRECENT  " + recent);
    }

    @Override
    public void close() {
        closed = true;
        if (demoTraffic != null) demoTraffic.stop();
        if (grpcTimeout != null) grpcTimeout.stop();
        if (grpcProbeStream != null) grpcProbeStream.cancel();
        if (grpcProbe != null) grpcProbe.close();
        pulseLayer.stop();
        try {
            subscription.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not detach topology traffic listener", exception);
        }
    }
}
