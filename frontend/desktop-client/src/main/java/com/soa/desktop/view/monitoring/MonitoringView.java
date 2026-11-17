package com.soa.desktop.view.monitoring;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.CreateAlertRequest;
import com.soa.desktop.client.rest.dto.CreateWorkOrderRequest;
import com.soa.desktop.client.rest.dto.MachineDto;
import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.orchestration.AlertDecisionPayload;
import com.soa.desktop.orchestration.AnomalyWorkflowOrchestrator;
import com.soa.desktop.ui.components.MetricCard;
import com.soa.desktop.ui.components.StatusChip;
import com.soa.desktop.ui.components.TelemetryChart;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import com.soa.desktop.util.ActivityLogger;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.anomaly.Severity;
import com.soa.telemetry.anomaly.Thresholds;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MonitoringView extends VBox {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    private final MaintenanceRestClient rest;
    private final InventorySoapClient soap;
    private final String grpcHost;
    private final int grpcPort;
    private final boolean demo;
    private String activeMachineId;
    private final StatusChip statusPill = new StatusChip("CONNECTING", "offline");
    private final Label selectedMachine = new Label();
    private final VBox fleetList = new VBox(5);
    private List<MachineDto> fleetMachines = List.of();
    private final Label lastUpdate = new Label("Waiting for first reading…");
    private final Label alertBanner = new Label("");
    private final Label backendInfo = new Label("");
    private final MetricCard temperature = new MetricCard("TEMPERATURE", "°C", 75, 85);
    private final MetricCard vibration = new MetricCard("VIBRATION", "mm/s", 4, 7);
    private final MetricCard pressure = new MetricCard("PRESSURE", "bar", 6, 7);
    private final MetricCard energy = new MetricCard("ENERGY", "kW", 12, 15);
    private final TelemetryChart temperatureChart = new TelemetryChart();
    private GrpcTelemetryClient grpc;
    private AnomalyWorkflowOrchestrator orchestrator;
    private Timeline demoReadings;
    private int demoTick;

    public MonitoringView(String grpcHost, int grpcPort, String machineId,
                          MaintenanceRestClient rest, InventorySoapClient soap, boolean demo) {
        this.activeMachineId = machineId;
        this.grpcHost = grpcHost;
        this.grpcPort = grpcPort;
        this.rest = rest;
        this.soap = soap;
        this.demo = demo;
        getStyleClass().add("page");
        setSpacing(18);
        setPadding(new Insets(8));
        buildLayout();
        if (demo) {
            showFleet(List.of(
                    new MachineDto("MACHINE-001", "Press line 01", "RUNNING", "Assembly", null),
                    new MachineDto("MACHINE-002", "Cooling loop 04", "WARNING", "Utilities", null),
                    new MachineDto("MACHINE-003", "Conveyor 08", "CRITICAL", "Packaging", null),
                    new MachineDto("MACHINE-004", "CNC cell 02", "OFFLINE", "Machining", null)));
            statusPill.setText("SIMULATED");
            statusPill.setState("success");
            startDemoReadings();
        } else {
            loadFleet();
            connectStream(activeMachineId);
        }
    }

    private void buildLayout() {
        VBox header = new VBox(7);
        Label sectionId = new Label("02 / FLEET TELEMETRY");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Machine inspection");
        title.getStyleClass().add("page-title");
        HBox titleRow = new HBox(14);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        selectedMachine.setText(activeMachineId);
        selectedMachine.getStyleClass().add("mono-muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, selectedMachine, spacer, statusPill);
        header.getChildren().addAll(sectionId, titleRow);

        GridPane metrics = new GridPane();
        metrics.getStyleClass().add("metric-grid");
        MetricCard[] cards = {temperature, vibration, pressure, energy};
        for (int i = 0; i < cards.length; i++) {
            javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints();
            column.setPercentWidth(25);
            metrics.getColumnConstraints().add(column);
            metrics.add(cards[i], i, 0);
            GridPane.setHgrow(cards[i], Priority.ALWAYS);
        }
        Motion.stagger(java.util.List.of(cards));

        VBox chartPanel = new VBox(11);
        chartPanel.getStyleClass().add("surface-card");
        Label chartHeading = new Label("TEMPERATURE / LIVE TRACE");
        chartHeading.getStyleClass().add("panel-heading");
        chartPanel.getChildren().addAll(chartHeading, temperatureChart);
        VBox.setVgrow(temperatureChart, Priority.ALWAYS);

        alertBanner.getStyleClass().add("alert-banner");
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
        alertBanner.setWrapText(true);
        VBox workflow = new VBox(8);
        workflow.getStyleClass().add("surface-card");
        Label workflowTitle = new Label("ANOMALY WORKFLOW");
        workflowTitle.getStyleClass().add("panel-heading");
        backendInfo.getStyleClass().add("mono-muted");
        lastUpdate.getStyleClass().add("mono-muted");
        workflow.getChildren().addAll(workflowTitle, backendInfo, lastUpdate);

        VBox fleetPanel = new VBox(12);
        fleetPanel.getStyleClass().add("surface-card");
        Label fleetHeading = new Label("MACHINE FLEET");
        fleetHeading.getStyleClass().add("panel-heading");
        fleetList.setPadding(new Insets(3, 0, 0, 0));
        fleetPanel.getChildren().addAll(fleetHeading, fleetList);
        fleetPanel.setPrefWidth(245);
        fleetPanel.setMinWidth(215);

        VBox detail = new VBox(14, metrics, alertBanner, chartPanel, workflow);
        HBox inspection = new HBox(14, fleetPanel, detail);
        HBox.setHgrow(detail, Priority.ALWAYS);
        VBox.setVgrow(chartPanel, Priority.ALWAYS);
        VBox.setVgrow(inspection, Priority.ALWAYS);
        getChildren().addAll(header, inspection);
    }

    private void loadFleet() {
        rest.getMachines().whenComplete((machines, error) -> Platform.runLater(() -> {
            if (error != null) {
                fleetList.getChildren().setAll(new Label("Machine list unavailable · " + error.getMessage()));
                return;
            }
            showFleet(machines);
        }));
    }

    private void showFleet(List<MachineDto> machines) {
        fleetMachines = List.copyOf(machines);
        renderFleet();
    }

    private void renderFleet() {
        fleetList.getChildren().clear();
        fleetMachines.forEach(machine -> {
            HBox row = new HBox(8);
            row.getStyleClass().add("machine-row");
            row.setAlignment(Pos.CENTER_LEFT);
            VBox details = new VBox(3);
            Label id = new Label(machine.id());
            id.getStyleClass().add("mono-text");
            Label name = new Label(machine.name() == null ? "Industrial unit" : machine.name());
            name.getStyleClass().add("body-muted");
            details.getChildren().addAll(id, name);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            String state = machine.status() == null ? "offline" : machine.status().toLowerCase();
            StatusChip status = new StatusChip(machine.status() == null ? "UNKNOWN" : machine.status(), state);
            row.getChildren().addAll(details, spacer, status);
            if (machine.id().equals(activeMachineId)) row.getStyleClass().add("active-row");
            row.setOnMouseClicked(event -> selectMachine(machine.id()));
            fleetList.getChildren().add(row);
        });
        Motion.stagger(List.copyOf(fleetList.getChildren()));
    }

    private void selectMachine(String machineId) {
        if (machineId == null || machineId.equals(activeMachineId)) return;
        activeMachineId = machineId;
        selectedMachine.setText(activeMachineId);
        renderFleet();
        temperature.setOffline();
        vibration.setOffline();
        pressure.setOffline();
        energy.setOffline();
        temperatureChart.clearSamples();
        if (demo) {
            statusPill.setText("SIMULATED");
            startDemoReadings();
        } else {
            connectStream(machineId);
        }
    }

    private void connectStream(String machineId) {
        if (orchestrator != null) orchestrator.close();
        if (grpc != null) grpc.close();
        statusPill.setText("CONNECTING");
        statusPill.setState("offline");
        grpc = new GrpcTelemetryClient(grpcHost, grpcPort);
        ActivityLogger.log("gRPC", "StreamMachineTelemetry", "PENDING", "Connecting to " + machineId);
        orchestrator = new AnomalyWorkflowOrchestrator(
                grpc, Thresholds.defaults(), machineId,
                this::onReading, this::onNewAlert, this::onCleared, this::onConnectionError);
        orchestrator.start();
    }

    private void onReading(TelemetryReading reading) {
        Platform.runLater(() -> {
            temperature.updateValue(reading.getTemperatureC());
            vibration.updateValue(reading.getVibrationMmS());
            pressure.updateValue(reading.getPressureBar());
            energy.updateValue(reading.getEnergyKw());
            temperatureChart.addSample(reading.getTemperatureC());
            lastUpdate.setText("Last reading · " + TIME_FMT.format(Instant.ofEpochMilli(reading.getTimestampEpochMs())));
            Severity severity = Thresholds.defaults().classifyAll(
                    reading.getTemperatureC(), reading.getVibrationMmS(),
                    reading.getPressureBar(), reading.getEnergyKw());
            statusPill.setText(severity == Severity.NORMAL ? "RUNNING" : severity.name());
            statusPill.setState(severity == Severity.NORMAL ? "success" : severity.name().toLowerCase());
        });
    }

    private void onNewAlert(AlertDecisionPayload alert) {
        String bannerText = String.format("NEW %s ANOMALY — %s — observed %.2f (threshold %.2f)",
                alert.severity(), alert.metric(), alert.observedValue(), alert.thresholdValue());
        showAlertBanner(bannerText, alert.severity());
        Platform.runLater(() -> backendInfo.setText("Creating alert on maintenance service…"));

        var request = new CreateAlertRequest(alert.machineId(), alert.metric().name(), alert.severity().name(),
                alert.observedValue(), alert.thresholdValue(), bannerText, alert.occurredAt());
        rest.createAlert(request).whenComplete((alertDto, error) -> {
            if (error != null) {
                Platform.runLater(() -> backendInfo.setText("Alert creation failed: " + error.getMessage()));
                return;
            }
            Platform.runLater(() -> backendInfo.setText("Alert persisted · " + alertDto.id() + " · creating work order…"));
            var workOrderRequest = new CreateWorkOrderRequest(alert.machineId(), alertDto.id(),
                    "Inspect " + alert.metric().name().toLowerCase() + " anomaly", bannerText,
                    alert.severity() == Severity.CRITICAL ? "HIGH" : "MEDIUM");
            rest.createWorkOrder(workOrderRequest).whenComplete((workOrder, workOrderError) -> {
                if (workOrderError != null) {
                    Platform.runLater(() -> backendInfo.setText("Work order failed: " + workOrderError.getMessage()));
                    return;
                }
                Platform.runLater(() -> backendInfo.setText("Moving work order to CHECKING_INVENTORY…"));
                rest.updateWorkOrderStatus(workOrder.id(), "CHECKING_INVENTORY").whenComplete((checking, statusError) -> {
                    if (statusError != null) {
                        Platform.runLater(() -> backendInfo.setText("Status transition failed: " + statusError.getMessage()));
                        return;
                    }
                    Platform.runLater(() -> backendInfo.setText("Checking spare-part stock via SOAP…"));
                    if (soap == null) {
                        Platform.runLater(() -> backendInfo.setText("SOAP client is still initializing."));
                        return;
                    }
                    String partId = "PART-001";
                    int quantity = 1;
                    soap.checkStock(partId, quantity).whenComplete((stock, stockError) -> {
                        if (stockError != null || stock == null || !stock.isAvailable()) {
                            Platform.runLater(() -> backendInfo.setText("Stock check failed or part unavailable."));
                            return;
                        }
                        String requestId = UUID.randomUUID().toString();
                        soap.reservePart(partId, quantity, requestId, workOrder.id()).whenComplete((reservation, reserveError) -> {
                            if (reserveError != null) {
                                Platform.runLater(() -> backendInfo.setText("Reservation failed: " + reserveError.getMessage()));
                                return;
                            }
                            Platform.runLater(() -> backendInfo.setText("Reserved · linking part to work order…"));
                            rest.associateReservation(workOrder.id(), reservation.getReservationId(), partId, quantity)
                                    .whenComplete((linked, linkError) -> Platform.runLater(() ->
                                            backendInfo.setText(linkError == null
                                                    ? "SUCCESS · Alert → work order → reserved part " + reservation.getReservationId()
                                                    : "Reservation link failed: " + linkError.getMessage())));
                        });
                    });
                });
            });
        });
    }

    private void onCleared(AlertDecisionPayload alert) {
        showAlertBanner("CLEARED — " + alert.metric() + " returned to normal (was " + alert.severity() + ")", null);
    }

    private void onConnectionError(Throwable error) {
        ActivityLogger.log("gRPC", "StreamMachineTelemetry", "FAILED", error.getMessage());
        Platform.runLater(() -> {
            statusPill.setText("DISCONNECTED");
            statusPill.setState("offline");
            lastUpdate.setText("Stream error · " + error.getMessage());
            temperature.setOffline();
            vibration.setOffline();
            pressure.setOffline();
            energy.setOffline();
        });
    }

    private void showAlertBanner(String text, Severity severity) {
        Platform.runLater(() -> {
            alertBanner.setText(text);
            alertBanner.pseudoClassStateChanged(CRITICAL, severity == Severity.CRITICAL);
            alertBanner.pseudoClassStateChanged(WARNING, severity == Severity.WARNING);
            alertBanner.pseudoClassStateChanged(SUCCESS, severity == null);
            alertBanner.setVisible(true);
            alertBanner.setManaged(true);
            Motion.enter(alertBanner).play();
        });
    }

    private void startDemoReadings() {
        if (demoReadings != null) demoReadings.stop();
        double[] current = {71, 3.2, 5.4, 11.6};
        TrafficEventBus.publish("gRPC", "REQUEST", "PENDING");
        demoReadings = new Timeline(new KeyFrame(Duration.millis(760), event -> {
            current[0] = bounded(current[0] + delta(4), 55, 94);
            current[1] = bounded(current[1] + delta(0.5), 1.4, 7.8);
            current[2] = bounded(current[2] + delta(0.3), 3.5, 7.6);
            current[3] = bounded(current[3] + delta(0.8), 7, 17);
            temperature.updateValue(current[0]);
            vibration.updateValue(current[1]);
            pressure.updateValue(current[2]);
            energy.updateValue(current[3]);
            temperatureChart.addSample(current[0]);
            lastUpdate.setText("Simulated reading · " + TIME_FMT.format(Instant.now()));
            Severity severity = Thresholds.defaults().classifyAll(current[0], current[1], current[2], current[3]);
            statusPill.setText(severity == Severity.NORMAL ? "RUNNING" : severity.name());
            statusPill.setState(severity == Severity.NORMAL ? "success" : severity.name().toLowerCase());
            if (++demoTick % 18 == 0 && severity != Severity.NORMAL) {
                alertBanner.setText("DEMO ANOMALY · " + severity + " · " + activeMachineId);
                alertBanner.pseudoClassStateChanged(CRITICAL, severity == Severity.CRITICAL);
                alertBanner.pseudoClassStateChanged(WARNING, severity == Severity.WARNING);
                alertBanner.setVisible(true);
                alertBanner.setManaged(true);
                backendInfo.setText("Demo mode · alert → work order → inventory flow simulated.");
            }
            TrafficEventBus.publish("gRPC", "RESPONSE", "SUCCESS");
        }));
        demoReadings.setCycleCount(Timeline.INDEFINITE);
        demoReadings.play();
    }

    private static double delta(double magnitude) {
        return ThreadLocalRandom.current().nextDouble(-magnitude, magnitude);
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public void dispose() {
        if (demoReadings != null) demoReadings.stop();
        if (orchestrator != null) orchestrator.close();
        if (grpc != null) grpc.close();
    }
}
