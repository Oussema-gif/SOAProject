package com.soa.desktop.view.dashboard;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.AlertDto;
import com.soa.desktop.client.rest.dto.MachineDto;
import com.soa.desktop.client.rest.dto.PageResponse;
import com.soa.desktop.client.rest.dto.WorkOrderDto;
import com.soa.desktop.ui.components.MetricCard;
import com.soa.desktop.ui.components.StatusChip;
import com.soa.desktop.ui.components.TelemetryChart;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import com.soa.telemetry.TelemetryReading;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class DashboardView extends VBox implements AutoCloseable {

    private final MaintenanceRestClient rest;
    private final boolean demo;
    private final Runnable onViewAlerts;
    private final Runnable onViewWorkOrders;
    private final MetricCard temperature = new MetricCard("TEMPERATURE", "°C", 75, 85);
    private final MetricCard vibration = new MetricCard("VIBRATION", "mm/s", 4, 7);
    private final MetricCard pressure = new MetricCard("PRESSURE", "bar", 6, 7);
    private final MetricCard energy = new MetricCard("ENERGY", "kW", 12, 15);
    private final MetricCard activeAlerts = new MetricCard("ACTIVE ALERTS", "INCIDENTS", 4, 8);
    private final MetricCard workOrders = new MetricCard("OPEN WORK ORDERS", "ORDERS", 8, 14);
    private final MetricCard machineCount = new MetricCard("REGISTERED MACHINES", "UNITS", 20, 32);
    private final MetricCard runningMachines = new MetricCard("RUNNING MACHINES", "UNITS", 1, 0);
    private final TelemetryChart temperatureChart = new TelemetryChart();
    private final VBox machineLedger = new VBox(4);
    private final VBox incidentLedger = new VBox(4);
    private final VBox maintenanceLedger = new VBox(4);
    private final ComboBox<String> metricSelector = new ComboBox<>();
    private final Label chartHeading = new Label();
    private final ArrayDeque<TelemetryReading> history = new ArrayDeque<>();
    private final List<MetricCard> telemetryCards;
    private GrpcTelemetryClient grpc;
    private GrpcTelemetryClient.Cancellable stream;
    private Timeline demoReadings;

    public DashboardView(MaintenanceRestClient rest, boolean demo, Runnable onViewAlerts,
                         Runnable onViewMachines, Runnable onViewWorkOrders) {
        this.rest = rest;
        this.demo = demo;
        this.onViewAlerts = onViewAlerts;
        this.onViewWorkOrders = onViewWorkOrders;
        telemetryCards = List.of(temperature, vibration, pressure, energy);
        getStyleClass().add("page");
        setSpacing(20);
        setPadding(new Insets(8));

        VBox header = new VBox(7);
        Label eyebrow = new Label("01 / OPERATIONS");
        eyebrow.getStyleClass().add("section-label");
        Label title = new Label("Telemetry command deck");
        title.getStyleClass().add("page-title");
        Label description = new Label("Live readings from MACHINE-001 with backend-backed fleet and maintenance status.");
        description.getStyleClass().add("body-muted");
        header.getChildren().addAll(eyebrow, title, description);

        GridPane metrics = new GridPane();
        metrics.getStyleClass().add("metric-grid");
        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            metrics.getColumnConstraints().add(column);
        }
        List<MetricCard> allMetrics = List.of(temperature, vibration, pressure, energy,
                activeAlerts, workOrders, machineCount, runningMachines);
        for (int i = 0; i < allMetrics.size(); i++) {
            MetricCard card = allMetrics.get(i);
            metrics.add(card, i % 4, i / 4);
            GridPane.setHgrow(card, Priority.ALWAYS);
        }

        VBox chartPanel = new VBox(12);
        chartPanel.getStyleClass().add("surface-card");
        HBox chartTitle = new HBox(10);
        chartTitle.setAlignment(Pos.CENTER_LEFT);
        chartHeading.setText("MACHINE-001 / LIVE TEMPERATURE");
        chartHeading.getStyleClass().add("panel-heading");
        metricSelector.setItems(FXCollections.observableArrayList("TEMPERATURE", "VIBRATION", "PRESSURE", "ENERGY"));
        metricSelector.setValue("TEMPERATURE");
        metricSelector.valueProperty().addListener((observable, oldValue, metric) -> renderHistory());
        Region chartSpacer = new Region();
        HBox.setHgrow(chartSpacer, Priority.ALWAYS);
        StatusChip streamStatus = new StatusChip(demo ? "SIMULATED" : "CONNECTING", demo ? "warning" : "offline");
        chartTitle.getChildren().addAll(chartHeading, metricSelector, chartSpacer, streamStatus);
        chartPanel.getChildren().addAll(chartTitle, temperatureChart);
        VBox.setVgrow(temperatureChart, Priority.ALWAYS);

        VBox machinePanel = new VBox(10);
        machinePanel.getStyleClass().add("surface-card");
        HBox ledgerTitleRow = new HBox(8);
        ledgerTitleRow.setAlignment(Pos.CENTER_LEFT);
        Label ledgerTitle = new Label("MACHINE FLEET");
        ledgerTitle.getStyleClass().add("panel-heading");
        Region ledgerSpacer = new Region();
        HBox.setHgrow(ledgerSpacer, Priority.ALWAYS);
        Button viewMachines = new Button("OPEN FLEET →");
        viewMachines.getStyleClass().add("text-link");
        viewMachines.setOnAction(event -> onViewMachines.run());
        ledgerTitleRow.getChildren().addAll(ledgerTitle, ledgerSpacer, viewMachines);
        machineLedger.setPadding(new Insets(4, 0, 0, 0));
        machinePanel.getChildren().addAll(ledgerTitleRow, machineLedger);

        VBox incidentPanel = createFeedPanel("RECENT INCIDENTS", incidentLedger, onViewAlerts);
        VBox maintenancePanel = createFeedPanel("RECENT MAINTENANCE", maintenanceLedger, onViewWorkOrders);
        VBox rightColumn = new VBox(12, machinePanel, incidentPanel, maintenancePanel);
        HBox content = new HBox(16, chartPanel, rightColumn);
        HBox.setHgrow(chartPanel, Priority.ALWAYS);
        chartPanel.setPrefWidth(640);
        rightColumn.setPrefWidth(350);
        VBox.setVgrow(content, Priority.ALWAYS);

        Button alertsLink = new Button("VIEW ACTIVE ALERTS →");
        alertsLink.getStyleClass().add("text-link");
        alertsLink.setOnAction(event -> onViewAlerts.run());
        getChildren().addAll(header, metrics, content, alertsLink);
        VBox.setVgrow(content, Priority.ALWAYS);
        Motion.stagger(new ArrayList<>(allMetrics));

        if (demo) {
            populateDemoFleet();
            activeAlerts.updateValue(3);
            workOrders.updateValue(5);
            machineCount.updateValue(12);
            runningMachines.updateValue(3, "success");
            incidentLedger.getChildren().setAll(new Label("Demo events are not connected to a service."));
            maintenanceLedger.getChildren().setAll(new Label("Demo workflow is simulated."));
            startDemoReadings();
        } else {
            loadOperations();
            startTelemetry(streamStatus);
        }
    }

    private void loadOperations() {
        rest.getMachines().whenComplete((machines, error) -> Platform.runLater(() -> {
            if (error != null) {
                machineCount.setOffline();
                return;
            }
            showFleet(machines);
        }));
        rest.getMachinePage(0, 1, null).whenComplete((page, error) -> Platform.runLater(() -> {
            if (error == null) machineCount.updateValue(page.totalElements());
            else machineCount.setOffline();
        }));
        rest.getMachinePage(0, 1, "RUNNING").whenComplete((page, error) -> Platform.runLater(() -> {
            if (error == null) runningMachines.updateValue(page.totalElements(),
                    page.totalElements() > 0 ? "success" : "warning");
            else runningMachines.setOffline();
        }));
        rest.getAlertPage(0, 20, "ACTIVE").whenComplete((page, error) -> Platform.runLater(() -> {
            if (error == null) {
                activeAlerts.updateValue(page.totalElements());
                showIncidents(page.content());
            } else {
                activeAlerts.setOffline();
                incidentLedger.getChildren().setAll(new Label("Incident feed unavailable."));
            }
        }));
        rest.getWorkOrderPage(0, 20).whenComplete((page, error) -> Platform.runLater(() -> {
            if (error == null) {
                showMaintenance(page.content());
            } else {
                maintenanceLedger.getChildren().setAll(new Label("Maintenance feed unavailable."));
            }
        }));
        List<String> openStatuses = List.of("OPEN", "CHECKING_INVENTORY", "PENDING_RESERVATION",
                "WAITING_FOR_PARTS", "READY_FOR_MAINTENANCE", "IN_PROGRESS");
        List<CompletableFuture<PageResponse<WorkOrderDto>>> counts = new ArrayList<>();
        for (String status : openStatuses) counts.add(rest.getWorkOrderPage(0, 1, status));
        CompletableFuture.allOf(counts.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        workOrders.setOffline();
                        return;
                    }
                    long totalOpen = counts.stream().map(CompletableFuture::join)
                            .mapToLong(PageResponse::totalElements).sum();
                    workOrders.updateValue(totalOpen);
                }));
    }

    private VBox createFeedPanel(String title, VBox rows, Runnable navigate) {
        VBox panel = new VBox(9);
        panel.getStyleClass().add("surface-card");
        HBox heading = new HBox(8);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(title);
        label.getStyleClass().add("panel-heading");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button open = new Button("OPEN →");
        open.getStyleClass().add("text-link");
        open.setOnAction(event -> navigate.run());
        heading.getChildren().addAll(label, spacer, open);
        panel.getChildren().addAll(heading, rows);
        return panel;
    }

    private void showIncidents(List<AlertDto> alerts) {
        incidentLedger.getChildren().clear();
        if (alerts.isEmpty()) {
            incidentLedger.getChildren().add(new Label("No active incidents reported."));
            return;
        }
        alerts.stream().limit(3).forEach(alert -> {
            HBox row = new HBox(8);
            row.getStyleClass().add("machine-row");
            Label message = new Label(alert.machineId() + " · " + alert.metricType()
                    + " · " + String.format("%.2f", alert.observedValue()));
            message.getStyleClass().add("mono-muted");
            StatusChip severity = new StatusChip(alert.severity(),
                    alert.severity() == null ? "offline" : alert.severity().toLowerCase());
            row.getChildren().addAll(message, severity);
            row.setOnMouseClicked(event -> onViewAlerts.run());
            incidentLedger.getChildren().add(row);
        });
    }

    private void showMaintenance(List<WorkOrderDto> orders) {
        maintenanceLedger.getChildren().clear();
        List<WorkOrderDto> openOrders = orders.stream()
                .filter(order -> !"COMPLETED".equalsIgnoreCase(order.status())
                        && !"CANCELLED".equalsIgnoreCase(order.status()))
                .limit(3).toList();
        if (openOrders.isEmpty()) {
            maintenanceLedger.getChildren().add(new Label("No open maintenance activity."));
            return;
        }
        openOrders.forEach(order -> {
            Label row = new Label(order.id() + " · " + order.title() + " · "
                    + order.status().replace('_', ' '));
            row.getStyleClass().add("mono-muted");
            maintenanceLedger.getChildren().add(row);
        });
    }

    private void startTelemetry(StatusChip streamStatus) {
        grpc = new GrpcTelemetryClient("localhost", 9090);
        stream = grpc.stream("MACHINE-001", reading -> Platform.runLater(() -> {
            applyReading(reading);
            streamStatus.setText("STREAM LIVE");
            streamStatus.setState("success");
        }), error -> Platform.runLater(() -> {
            telemetryCards.forEach(MetricCard::setOffline);
            streamStatus.setText("STREAM OFFLINE");
            streamStatus.setState("offline");
        }));
    }

    private void applyReading(TelemetryReading reading) {
        temperature.updateValue(reading.getTemperatureC());
        vibration.updateValue(reading.getVibrationMmS());
        pressure.updateValue(reading.getPressureBar());
        energy.updateValue(reading.getEnergyKw());
        history.addLast(reading);
        while (history.size() > 72) history.removeFirst();
        renderHistory();
    }

    private void renderHistory() {
        String metric = metricSelector.getValue() == null ? "TEMPERATURE" : metricSelector.getValue();
        chartHeading.setText("MACHINE-001 / LIVE " + metric);
        double warning = switch (metric) {
            case "VIBRATION" -> 4;
            case "PRESSURE" -> 6;
            case "ENERGY" -> 12;
            default -> 75;
        };
        double critical = switch (metric) {
            case "VIBRATION" -> 7;
            case "PRESSURE" -> 7;
            case "ENERGY" -> 15;
            default -> 85;
        };
        temperatureChart.clearSamples();
        temperatureChart.setThresholds(warning, critical);
        for (TelemetryReading reading : history) {
            temperatureChart.addSample(metricValue(reading, metric),
                    Instant.ofEpochMilli(reading.getTimestampEpochMs()));
        }
    }

    private static double metricValue(TelemetryReading reading, String metric) {
        return switch (metric) {
            case "VIBRATION" -> reading.getVibrationMmS();
            case "PRESSURE" -> reading.getPressureBar();
            case "ENERGY" -> reading.getEnergyKw();
            default -> reading.getTemperatureC();
        };
    }

    private void startDemoReadings() {
        double[] current = {56, 2.2, 4.1, 8.6};
        TrafficEventBus.publish("gRPC", "REQUEST", "PENDING");
        demoReadings = new Timeline(new KeyFrame(Duration.millis(850), event -> {
            current[0] = bounded(current[0] + randomDelta(3), 52, 94);
            current[1] = bounded(current[1] + randomDelta(0.35), 1.2, 7.8);
            current[2] = bounded(current[2] + randomDelta(0.24), 3.2, 7.6);
            current[3] = bounded(current[3] + randomDelta(0.65), 7, 17);
            temperature.updateValue(current[0]);
            vibration.updateValue(current[1]);
            pressure.updateValue(current[2]);
            energy.updateValue(current[3]);
            long timestamp = System.currentTimeMillis();
            history.addLast(TelemetryReading.newBuilder().setTemperatureC((float) current[0])
                    .setVibrationMmS((float) current[1]).setPressureBar((float) current[2])
                    .setEnergyKw((float) current[3]).setTimestampEpochMs(timestamp).build());
            while (history.size() > 72) history.removeFirst();
            renderHistory();
            TrafficEventBus.publish("gRPC", "RESPONSE", "SUCCESS");
        }));
        demoReadings.setCycleCount(Timeline.INDEFINITE);
        demoReadings.play();
    }

    private void populateDemoFleet() {
        showFleet(List.of(
                new MachineDto("MACHINE-001", "Press line 01", "RUNNING", "Assembly", null),
                new MachineDto("MACHINE-002", "Cooling loop 04", "WARNING", "Utilities", null),
                new MachineDto("MACHINE-003", "Conveyor 08", "CRITICAL", "Packaging", null),
                new MachineDto("MACHINE-004", "CNC cell 02", "OFFLINE", "Machining", null)));
    }

    private void showFleet(List<MachineDto> machines) {
        machineLedger.getChildren().clear();
        if (machines.isEmpty()) {
            Label empty = new Label("No machines are registered.");
            empty.getStyleClass().add("body-muted");
            machineLedger.getChildren().add(empty);
            return;
        }
        machines.stream().limit(5).forEach(machine -> {
            HBox row = new HBox(10);
            row.getStyleClass().add("machine-row");
            row.setAlignment(Pos.CENTER_LEFT);
            VBox identity = new VBox(3);
            Label id = new Label(machine.id());
            id.getStyleClass().add("mono-text");
            Label name = new Label(machine.name() == null ? "Industrial unit" : machine.name());
            name.getStyleClass().add("body-muted");
            identity.getChildren().addAll(id, name);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            String state = machine.status() == null ? "offline" : machine.status().toLowerCase();
            StatusChip status = new StatusChip(machine.status() == null ? "UNKNOWN" : machine.status(), state);
            row.getChildren().addAll(identity, spacer, status);
            machineLedger.getChildren().add(row);
        });
        Motion.stagger(new ArrayList<>(machineLedger.getChildren()));
    }

    private static double randomDelta(double magnitude) {
        return ThreadLocalRandom.current().nextDouble(-magnitude, magnitude);
    }

    private static double bounded(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void close() {
        if (demoReadings != null) demoReadings.stop();
        if (stream != null) stream.cancel();
        if (grpc != null) grpc.close();
    }
}
