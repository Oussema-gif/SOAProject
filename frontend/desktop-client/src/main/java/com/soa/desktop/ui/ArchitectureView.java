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
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.anomaly.Severity;
import com.soa.telemetry.anomaly.Thresholds;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ArchitectureView extends VBox implements AutoCloseable {

    private static final double MAX_NODE_WIDTH = 220;
    private static final double MIN_NODE_WIDTH = 140;
    private static final double MAX_COLUMN_GAP = 140;
    private static final double NODE_HEIGHT = 150;
    private static final double SERVICE_GAP = 20;

    private record Connection(Path path, TopologyNode from, TopologyNode to,
                              boolean fromRight, boolean toRight, boolean visible) { }

    private final boolean demo;
    private final MaintenanceRestClient rest;
    private final InventorySoapClient soap;
    private final Pane routePane = new Pane();
    private final PulseLayer pulseLayer = new PulseLayer();
    private final List<Connection> connections = new ArrayList<>();
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
        Label detail = new Label("Live service checks · telemetry updates continuously · persistence health is indirect.");
        detail.getStyleClass().add("body-muted");
        heading.getChildren().addAll(eyebrow, title, detail);

        routePane.getStyleClass().add("topology-canvas");
        routePane.setMinWidth(480);
        routePane.setPrefWidth(780);
        routePane.setMinHeight(500);
        routePane.setPrefHeight(520);
        routePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        pulseLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane layers = new StackPane(routePane, pulseLayer);
        layers.getStyleClass().add("topology-frame");
        VBox.setVgrow(layers, javafx.scene.layout.Priority.ALWAYS);

        VBox legend = new VBox(6);
        Label legendTitle = new Label("FLOW LEGEND");
        legendTitle.getStyleClass().add("section-label");
        Label legendText = new Label("REST / circular packet     SOAP / capsule packet     gRPC / compact packet");
        legendText.getStyleClass().add("mono-muted");
        Label stateLegend = new Label("Green / normal or connected     Amber / loading or warning     Red / critical alert     Gray / disconnected");
        stateLegend.getStyleClass().add("mono-muted");
        legend.getChildren().addAll(legendTitle, legendText, stateLegend);

        VBox details = new VBox(7);
        details.getStyleClass().add("surface-card");
        Label detailsHeading = new Label("SERVICE INSPECTION");
        detailsHeading.getStyleClass().add("panel-heading");
        serviceDetails.getStyleClass().add("mono-muted");
        serviceDetails.setWrapText(true);
        details.getChildren().addAll(detailsHeading, serviceDetails);
        getChildren().addAll(heading, layers, details, legend);
        buildTopology();
        subscription = TrafficEventBus.subscribe(event -> Platform.runLater(() -> handleTrafficEvent(event)));

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
        database.setHealth("warning", "INDIRECT");
        maintenance.setHealth(demo ? "warning" : "loading", demo ? "SIMULATED" : "CHECKING");
        inventory.setHealth(demo ? "warning" : "loading", demo ? "SIMULATED" : "CHECKING");
        telemetry.setHealth(demo ? "warning" : "loading", demo ? "SIMULATED" : "CHECKING");
        for (TopologyNode node : nodes.values()) {
            node.setOnMouseClicked(event -> showServiceDetails(node));
        }

        outboundRoutes.put("REST", connect(desktop, maintenance, true, false, "connection-rest", true));
        outboundRoutes.put("SOAP", connect(desktop, inventory, true, false, "connection-soap", true));
        outboundRoutes.put("GRPC", connect(desktop, telemetry, true, false, "connection-grpc", true));
        inboundRoutes.put("REST", connect(maintenance, desktop, false, true, "connection-rest", false));
        inboundRoutes.put("SOAP", connect(inventory, desktop, false, true, "connection-soap", false));
        inboundRoutes.put("GRPC", connect(telemetry, desktop, false, true, "connection-grpc", false));
        connect(maintenance, database, true, false, "connection-storage", true);
        connect(inventory, database, true, false, "connection-storage", true);
        connect(telemetry, database, true, false, "connection-storage", true);

        routePane.getChildren().addAll(outboundRoutes.values());
        connections.stream().filter(Connection::visible).map(Connection::path)
                .filter(path -> !routePane.getChildren().contains(path))
                .forEach(routePane.getChildren()::add);
        routePane.getChildren().addAll(desktop, maintenance, inventory, telemetry, database);
        routePane.widthProperty().addListener((obs, oldWidth, newWidth) -> layoutTopology());
        routePane.heightProperty().addListener((obs, oldHeight, newHeight) -> layoutTopology());
        layoutTopology();
        Motion.stagger(routePane.getChildren());
    }

    private Path connect(TopologyNode from, TopologyNode to, boolean fromRight, boolean toRight,
                         String styleClass, boolean visible) {
        Path path = new Path();
        path.getStyleClass().addAll("topology-connection", styleClass);
        path.setCache(true);
        path.setCacheHint(javafx.scene.CacheHint.SPEED);
        connections.add(new Connection(path, from, to, fromRight, toRight, visible));
        return path;
    }

    private void layoutTopology() {
        double width = routePane.getWidth();
        if (width <= 0) return;

        double nodeWidth = Math.min(MAX_NODE_WIDTH, Math.max(MIN_NODE_WIDTH, (width - 48) / 3));
        double columnGap = Math.min(MAX_COLUMN_GAP,
                Math.max(12, (width - 3 * nodeWidth - 24) / 2));
        double graphWidth = 3 * nodeWidth + 2 * columnGap;
        double left = (width - graphWidth) / 2;
        double middle = left + nodeWidth + columnGap;
        double right = middle + nodeWidth + columnGap;
        double serviceHeight = NODE_HEIGHT * 3 + SERVICE_GAP * 2;
        double top = Math.max(12, (routePane.getHeight() - serviceHeight) / 2);
        double middleY = top + NODE_HEIGHT + SERVICE_GAP;

        nodes.values().forEach(node -> {
            node.setPrefWidth(nodeWidth);
            node.setMaxWidth(nodeWidth);
        });
        nodes.get("CLIENT").resizeRelocate(left, middleY, nodeWidth, NODE_HEIGHT);
        nodes.get("REST").resizeRelocate(middle, top, nodeWidth, NODE_HEIGHT);
        nodes.get("SOAP").resizeRelocate(middle, middleY, nodeWidth, NODE_HEIGHT);
        nodes.get("GRPC").resizeRelocate(middle, top + (NODE_HEIGHT + SERVICE_GAP) * 2,
                nodeWidth, NODE_HEIGHT);
        nodes.get("DATABASE").resizeRelocate(right, middleY, nodeWidth, NODE_HEIGHT);

        for (Connection connection : connections) {
            double startX = connection.from().getLayoutX()
                    + (connection.fromRight() ? connection.from().getWidth() : 0);
            double startY = connection.from().getLayoutY() + connection.from().getHeight() / 2;
            double endX = connection.to().getLayoutX()
                    + (connection.toRight() ? connection.to().getWidth() : 0);
            double endY = connection.to().getLayoutY() + connection.to().getHeight() / 2;
            double bend = Math.max(24, Math.abs(endX - startX) * 0.45);
            double direction = endX >= startX ? 1 : -1;
            connection.path().getElements().setAll(
                    new MoveTo(startX, startY),
                    new CubicCurveTo(startX + direction * bend, startY,
                            endX - direction * bend, endY, endX, endY));
        }
    }

    private void handleTrafficEvent(TrafficEventBus.TrafficEvent event) {
        if (closed || event == null || event.protocol() == null || event.direction() == null) return;
        String protocol = event.protocol().toUpperCase(Locale.ROOT);
        TopologyNode node = nodes.get(protocol);
        String direction = event.direction().toUpperCase(Locale.ROOT);
        String status = event.status() == null ? "" : event.status().toUpperCase(Locale.ROOT);

        if ("STATE".equals(direction)) {
            switch (status) {
                case "NORMAL", "SUCCESS" -> {
                    if (node != null) node.setHealth("success", "NORMAL · LIVE");
                }
                case "WARNING" -> {
                    if (node != null) node.setHealth("warning", "ALERT · WARNING");
                }
                case "CRITICAL" -> {
                    if (node != null) node.setHealth("critical", "ALERT · CRITICAL");
                }
                default -> { }
            }
            return;
        }

        Path route = "RESPONSE".equals(direction) ? inboundRoutes.get(protocol) : outboundRoutes.get(protocol);
        if (route != null) pulseLayer.pulse(route, event.protocol(), status);
        if ("REQUEST".equals(direction) && "PENDING".equals(status) && node != null) {
            node.setHealth("loading", "IN FLIGHT");
        } else if ("RESPONSE".equals(direction) && node != null) {
            if ("SUCCESS".equals(status)) node.setHealth("success", "CONNECTED");
            if ("FAILED".equals(status)) node.setHealth("offline", "DISCONNECTED");
        }
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
        AtomicBoolean firstReading = new AtomicBoolean();
        grpcTimeout = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            if (grpcProbeStream != null) grpcProbeStream.cancel();
            if (grpcProbe != null) grpcProbe.close();
            grpcProbe = null;
            grpcProbeStream = null;
            if (!closed) nodes.get("GRPC").setHealth("offline", "NO RESPONSE · 5s");
        }));
        grpcTimeout.play();
        grpcProbeStream = grpcProbe.stream(machine.id(), (TelemetryReading reading) -> Platform.runLater(() -> {
            if (closed) return;
            if (firstReading.compareAndSet(false, true)) {
                grpcTimeout.stop();
                long firstResponseMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                ActivityLogger.log("gRPC", "StreamMachineTelemetry", "SUCCESS",
                        "First reading received for " + machine.id(), null, null, firstResponseMillis);
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            Severity severity = Thresholds.defaults().classifyAll(
                    reading.getTemperatureC(), reading.getVibrationMmS(),
                    reading.getPressureBar(), reading.getEnergyKw());
            String status = severity == Severity.NORMAL
                    ? "LIVE · " + elapsedMillis + " ms"
                    : "ALERT · " + severity + " · LIVE · " + elapsedMillis + " ms";
            nodes.get("GRPC").setHealth(severity.name().toLowerCase(Locale.ROOT), status);
        }), error -> Platform.runLater(() -> {
            if (closed) return;
            grpcTimeout.stop();
            nodes.get("GRPC").setHealth("offline", "DOWN");
            if (grpcProbe != null) grpcProbe.close();
            grpcProbe = null;
            grpcProbeStream = null;
        }));
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
        serviceDetails.setText(node.getTitle() + " · " + node.getHealthText()
                + "\nENDPOINT  " + endpoint + "\nRECENT  " + recent);
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
