package com.soa.desktop.view.dashboard;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.MachineDto;
import com.soa.desktop.ui.components.MetricCard;
import com.soa.desktop.ui.components.StatusChip;
import com.soa.desktop.ui.components.TelemetryChart;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.anomaly.Severity;
import com.soa.telemetry.anomaly.Thresholds;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DashboardView extends VBox implements AutoCloseable {

    private final MaintenanceRestClient rest;
    private final boolean demo;
    private final MetricCard temperature = new MetricCard("TEMPERATURE", "°C", 75, 85);
    private final MetricCard vibration = new MetricCard("VIBRATION", "mm/s", 4, 7);
    private final MetricCard pressure = new MetricCard("PRESSURE", "bar", 6, 7);
    private final MetricCard energy = new MetricCard("ENERGY", "kW", 12, 15);
    private final MetricCard activeAlerts = new MetricCard("ACTIVE ALERTS", "INCIDENTS", 4, 8);
    private final MetricCard workOrders = new MetricCard("OPEN WORK ORDERS", "ORDERS", 8, 14);
    private final MetricCard machineCount = new MetricCard("REGISTERED MACHINES", "UNITS", 20, 32);
    private final MetricCard healthySystems = new MetricCard("SYSTEM HEALTH", "%", 100, 101);
    private final TelemetryChart temperatureChart = new TelemetryChart();
    private final VBox machineLedger = new VBox(4);
    private final Thresholds thresholds = Thresholds.defaults();
    private final List<MetricCard> telemetryCards;
    private GrpcTelemetryClient grpc;
    private GrpcTelemetryClient.Cancellable stream;
    private Timeline demoReadings;

    public DashboardView(MaintenanceRestClient rest, boolean demo, Runnable onViewAlerts, Runnable onViewMachines) {
        this.rest = rest;
        this.demo = demo;
        telemetryCards = List.of(temperature, vibration, pressure, energy);
        getStyleClass().add("page");
        setSpacing(20);
        setPadding(new Insets(8));

        VBox header = new VBox(7);
        Label eyebrow = new Label("01 / OPERATIONS");
        eyebrow.getStyleClass().add("section-label");
        Label title = new Label("Telemetry command deck");
        title.getStyleClass().add("page-title");
        Label description = new Label("Live machine telemetry, service health and maintenance activity.");
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
                activeAlerts, workOrders, machineCount, healthySystems);
        for (int i = 0; i < allMetrics.size(); i++) {
            MetricCard card = allMetrics.get(i);
            metrics.add(card, i % 4, i / 4);
            GridPane.setHgrow(card, Priority.ALWAYS);
        }

        VBox chartPanel = new VBox(12);
        chartPanel.getStyleClass().add("surface-card");
        HBox chartTitle = new HBox(10);
        chartTitle.setAlignment(Pos.CENTER_LEFT);
        Label chartHeading = new Label("TEMPERATURE / LIVE TRACE");
        chartHeading.getStyleClass().add("panel-heading");
        Region chartSpacer = new Region();
        HBox.setHgrow(chartSpacer, Priority.ALWAYS);
        StatusChip streamStatus = new StatusChip(demo ? "SIMULATED" : "CONNECTING", demo ? "success" : "offline");
        chartTitle.getChildren().addAll(chartHeading, chartSpacer, streamStatus);
        chartPanel.getChildren().addAll(chartTitle, temperatureChart);
        VBox.setVgrow(temperatureChart, Priority.ALWAYS);

        VBox ledger = new VBox(12);
        ledger.getStyleClass().add("surface-card");
        HBox ledgerTitleRow = new HBox(8);
        ledgerTitleRow.setAlignment(Pos.CENTER_LEFT);
        Label ledgerTitle = new Label("MACHINE FLEET");
        ledgerTitle.getStyleClass().add("panel-heading");
        Region ledgerSpacer = new Region();
        HBox.setHgrow(ledgerSpacer, Priority.ALWAYS);
        Label viewMachines = new Label("OPEN FLEET →");
        viewMachines.getStyleClass().add("mono-muted");
        viewMachines.setOnMouseClicked(event -> onViewMachines.run());
        ledgerTitleRow.getChildren().addAll(ledgerTitle, ledgerSpacer, viewMachines);
        machineLedger.setPadding(new Insets(4, 0, 0, 0));
        ledger.getChildren().addAll(ledgerTitleRow, machineLedger);
        HBox content = new HBox(16, chartPanel, ledger);
        HBox.setHgrow(chartPanel, Priority.ALWAYS);
        chartPanel.setPrefWidth(680);
        ledger.setPrefWidth(360);
        VBox.setVgrow(content, Priority.ALWAYS);

        Label alertsLink = new Label("VIEW ACTIVE ALERTS →");
        alertsLink.getStyleClass().add("mono-muted");
        alertsLink.setOnMouseClicked(event -> onViewAlerts.run());
        getChildren().addAll(header, metrics, content, alertsLink);
        VBox.setVgrow(content, Priority.ALWAYS);
        Motion.stagger(new ArrayList<>(allMetrics));

        if (demo) {
            populateDemoFleet();
            activeAlerts.updateValue(3);
            workOrders.updateValue(5);
            machineCount.updateValue(12);
            healthySystems.updateValue(96);
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
            machineCount.updateValue(machines.size());
            showFleet(machines);
        }));
        rest.getActiveAlerts().whenComplete((alerts, error) -> Platform.runLater(() -> {
            if (error == null) activeAlerts.updateValue(alerts.size());
            else activeAlerts.setOffline();
        }));
        rest.getWorkOrders().whenComplete((orders, error) -> Platform.runLater(() -> {
            if (error == null) workOrders.updateValue(orders.size());
            else workOrders.setOffline();
        }));
    }

    private void startTelemetry(StatusChip streamStatus) {
        grpc = new GrpcTelemetryClient("localhost", 9090);
        stream = grpc.stream("MACHINE-001", reading -> Platform.runLater(() -> {
            applyReading(reading);
            streamStatus.setText("STREAM LIVE");
            streamStatus.setState("success");
        }), error -> Platform.runLater(() -> {
            telemetryCards.forEach(MetricCard::setOffline);
            healthySystems.setOffline();
            streamStatus.setText("STREAM OFFLINE");
            streamStatus.setState("offline");
        }));
    }

    private void applyReading(TelemetryReading reading) {
        temperature.updateValue(reading.getTemperatureC());
        vibration.updateValue(reading.getVibrationMmS());
        pressure.updateValue(reading.getPressureBar());
        energy.updateValue(reading.getEnergyKw());
        Severity severity = thresholds.classifyAll(reading.getTemperatureC(),
                reading.getVibrationMmS(), reading.getPressureBar(), reading.getEnergyKw());
        double health = severity == Severity.NORMAL ? 100 : severity == Severity.WARNING ? 68 : 38;
        healthySystems.updateValue(health, health < 45 ? "critical" : health < 80 ? "warning" : "success");
        temperatureChart.addSample(reading.getTemperatureC());
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
            double health = current[0] > 85 || current[1] > 7 ? 42 : current[0] > 75 ? 74 : 97;
            healthySystems.updateValue(health, health < 45 ? "critical" : health < 80 ? "warning" : "success");
            temperatureChart.addSample(current[0]);
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
