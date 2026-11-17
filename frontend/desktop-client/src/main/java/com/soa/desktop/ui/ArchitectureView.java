package com.soa.desktop.ui;

import com.soa.desktop.ui.components.TopologyNode;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.PulseLayer;
import com.soa.desktop.ui.topology.TrafficEventBus;
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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ArchitectureView extends VBox implements AutoCloseable {

    private final boolean demo;
    private final Pane routePane = new Pane();
    private final PulseLayer pulseLayer = new PulseLayer();
    private final Map<String, Path> outboundRoutes = new HashMap<>();
    private final Map<String, Path> inboundRoutes = new HashMap<>();
    private final Timeline demoTraffic;
    private final AutoCloseable subscription;

    public ArchitectureView(boolean demo) {
        this.demo = demo;
        getStyleClass().add("page");
        setSpacing(20);
        setPadding(new Insets(8));

        VBox heading = new VBox(7);
        Label eyebrow = new Label("06 / NETWORK FABRIC");
        eyebrow.getStyleClass().add("section-label");
        Label title = new Label("Architecture / Live Topology");
        title.getStyleClass().add("page-title");
        Label detail = new Label("Live protocol paths · request and response packets");
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

        getChildren().addAll(heading, layers, legend);
        buildTopology();
        subscription = TrafficEventBus.subscribe(event -> Platform.runLater(() -> {
            Path route = "RESPONSE".equalsIgnoreCase(event.direction())
                    ? inboundRoutes.get(event.protocol().toUpperCase())
                    : outboundRoutes.get(event.protocol().toUpperCase());
            if (route != null) pulseLayer.pulse(route, event.protocol(), event.status());
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
        TopologyNode inventory = new TopologyNode("NODE 03", "Inventory", "SOAP", "Parts · :8082");
        TopologyNode telemetry = new TopologyNode("NODE 04", "Telemetry", "gRPC", "Stream · :9090");
        TopologyNode database = new TopologyNode("NODE 05", "PostgreSQL", "PERSISTENCE", "Industrial data");
        routePane.getChildren().addAll(desktop, maintenance, inventory, telemetry, database);

        double[] x = {18, 245, 245, 485, 775};
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

    @Override
    public void close() {
        if (demoTraffic != null) demoTraffic.stop();
        pulseLayer.stop();
        try {
            subscription.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not detach topology traffic listener", exception);
        }
    }
}
