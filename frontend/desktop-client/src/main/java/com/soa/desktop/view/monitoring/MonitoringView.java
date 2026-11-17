package com.soa.desktop.view.monitoring;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.AlertDto;
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
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class MonitoringView extends VBox {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    private final MaintenanceRestClient rest;
    private final InventorySoapClient soap;
    private final Map<String, CompletableFuture<AlertDto>> alertCreations =
            new ConcurrentHashMap<>();
    private final String grpcHost;
    private final int grpcPort;
    private final boolean demo;
    private String activeMachineId;
    private final StatusChip statusPill = new StatusChip("CONNECTING", "offline");
    private final Label selectedMachine = new Label();
    private final VBox fleetList = new VBox(5);
    private List<MachineDto> fleetMachines = List.of();
    private int fleetPageIndex;
    private int fleetTotal;
    private int fleetRequestVersion;
    private static final int FLEET_PAGE_SIZE = 20;
    private final Label lastUpdate = new Label("Waiting for first reading…");
    private final Label alertBanner = new Label("");
    private final Label backendInfo = new Label("");
    private final Label machineContext = new Label("Loading machine context…");
    private final TextField fleetSearch = new TextField();
    private final ComboBox<String> fleetStatus = new ComboBox<>();
    private final ComboBox<String> metricSelector = new ComboBox<>();
    private final Label chartHeading = new Label();
    private final Button fleetPrevious = new Button("←");
    private final Button fleetNext = new Button("→");
    private final Label fleetPageLabel = new Label("");
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(350));
    private final ArrayDeque<TelemetryReading> history = new ArrayDeque<>();
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
            loadMachineContext();
            statusPill.setText("SIMULATED");
            statusPill.setState("warning");
            startDemoReadings();
        } else {
            loadFleetPage(0);
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
        chartHeading.setText("LIVE TEMPERATURE");
        chartHeading.getStyleClass().add("panel-heading");
        metricSelector.setItems(FXCollections.observableArrayList("TEMPERATURE", "VIBRATION", "PRESSURE", "ENERGY"));
        metricSelector.setValue("TEMPERATURE");
        metricSelector.valueProperty().addListener((observable, oldValue, metric) -> renderHistory());
        HBox chartTitle = new HBox(10, chartHeading, metricSelector);
        chartTitle.setAlignment(Pos.CENTER_LEFT);
        chartPanel.getChildren().addAll(chartTitle, temperatureChart);
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
        fleetSearch.setPromptText("Filter machine");
        fleetStatus.setItems(FXCollections.observableArrayList(
                "ALL", "RUNNING", "WARNING", "CRITICAL", "OFFLINE", "MAINTENANCE"));
        fleetStatus.setValue("ALL");
        searchDebounce.setOnFinished(event -> loadFleetPage(0));
        fleetSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (demo) renderFleet();
            else {
                searchDebounce.stop();
                searchDebounce.playFromStart();
            }
        });
        fleetStatus.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (demo) renderFleet();
            else loadFleetPage(0);
        });
        fleetPrevious.getStyleClass().add("small-button");
        fleetNext.getStyleClass().add("small-button");
        fleetPrevious.setOnAction(event -> loadFleetPage(Math.max(0, fleetPageIndex - 1)));
        fleetNext.setOnAction(event -> loadFleetPage(fleetPageIndex + 1));
        fleetPageLabel.getStyleClass().add("mono-muted");
        HBox fleetPager = new HBox(6, fleetPrevious, fleetPageLabel, fleetNext);
        fleetPager.setAlignment(Pos.CENTER);
        fleetList.setPadding(new Insets(3, 0, 0, 0));
        machineContext.getStyleClass().add("mono-muted");
        machineContext.setWrapText(true);
        fleetPanel.getChildren().addAll(fleetHeading, fleetSearch, fleetStatus, fleetList, fleetPager, machineContext);
        fleetPanel.setPrefWidth(245);
        fleetPanel.setMinWidth(215);

        VBox detail = new VBox(14, metrics, alertBanner, chartPanel, workflow);
        HBox inspection = new HBox(14, fleetPanel, detail);
        HBox.setHgrow(detail, Priority.ALWAYS);
        VBox.setVgrow(chartPanel, Priority.ALWAYS);
        VBox.setVgrow(inspection, Priority.ALWAYS);
        getChildren().addAll(header, inspection);
    }

    private void loadFleetPage(int page) {
        fleetPageIndex = page;
        int requestVersion = ++fleetRequestVersion;
        searchDebounce.stop();
        String status = "ALL".equals(fleetStatus.getValue()) ? null : fleetStatus.getValue();
        String query = fleetSearch.getText() == null ? "" : fleetSearch.getText().trim();
        fleetList.getChildren().setAll(new Label("Loading machine page…"));
        rest.getMachinePage(page, FLEET_PAGE_SIZE, status, query).whenComplete((response, error) -> Platform.runLater(() -> {
            if (requestVersion != fleetRequestVersion) return;
            if (error != null) {
                fleetMachines = List.of();
                fleetTotal = 0;
                renderFleet();
                machineContext.setText("Machine list unavailable · " + error.getMessage());
                updateFleetPager();
                return;
            }
            fleetMachines = List.copyOf(response.content());
            fleetTotal = response.totalElements();
            renderFleet();
            loadMachineContext();
            updateFleetPager();
        }));
    }

    private void showFleet(List<MachineDto> machines) {
        fleetMachines = List.copyOf(machines);
        fleetTotal = machines.size();
        renderFleet();
        updateFleetPager();
    }

    private void updateFleetPager() {
        int pageCount = Math.max(1, (int) Math.ceil(fleetTotal / (double) FLEET_PAGE_SIZE));
        fleetPageLabel.setText(demo ? "DEMO" : (fleetPageIndex + 1) + " / " + pageCount);
        fleetPrevious.setDisable(demo || fleetPageIndex == 0);
        fleetNext.setDisable(demo || (fleetPageIndex + 1) * FLEET_PAGE_SIZE >= fleetTotal);
    }

    private void renderFleet() {
        fleetList.getChildren().clear();
        String query = fleetSearch.getText() == null ? "" : fleetSearch.getText().trim().toLowerCase();
        String statusFilter = fleetStatus.getValue();
        List<MachineDto> visibleMachines = fleetMachines.stream()
                .filter(machine -> query.isEmpty() || contains(machine.id(), query) || contains(machine.name(), query))
                .filter(machine -> statusFilter == null || "ALL".equals(statusFilter)
                        || statusFilter.equalsIgnoreCase(machine.status()))
                .toList();
        if (visibleMachines.isEmpty()) {
            Label empty = new Label(fleetMachines.isEmpty()
                    ? "No machines returned by the maintenance service."
                    : "No machines match the current filters.");
            empty.getStyleClass().add("body-muted");
            fleetList.getChildren().add(empty);
        }
        visibleMachines.forEach(machine -> {
            HBox row = new HBox(8);
            row.getStyleClass().add("machine-row");
            row.setAlignment(Pos.CENTER_LEFT);
            VBox details = new VBox(3);
            Label id = new Label(machine.id());
            id.getStyleClass().add("mono-text");
            String location = machine.location() == null ? "Location not reported" : machine.location();
            Label name = new Label((machine.name() == null ? "Industrial unit" : machine.name())
                    + " · " + location);
            name.getStyleClass().add("body-muted");
            Label lastSeen = new Label(machine.lastSeenAt() == null
                    ? "Last telemetry: not reported" : "Last seen · " + TIME_FMT.format(machine.lastSeenAt()));
            lastSeen.getStyleClass().add("mono-muted");
            details.getChildren().addAll(id, name, lastSeen);
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
        history.clear();
        renderFleet();
        loadMachineContext();
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
            history.addLast(reading);
            while (history.size() > 72) history.removeFirst();
            renderHistory();
            lastUpdate.setText("Last reading · " + TIME_FMT.format(Instant.ofEpochMilli(reading.getTimestampEpochMs())));
            Severity severity = Thresholds.defaults().classifyAll(
                    reading.getTemperatureC(), reading.getVibrationMmS(),
                    reading.getPressureBar(), reading.getEnergyKw());
            statusPill.setText(severity == Severity.NORMAL ? "RUNNING" : severity.name());
            statusPill.setState(severity == Severity.NORMAL ? "success" : severity.name().toLowerCase());
            TrafficEventBus.publish("gRPC", "STATE", severity.name());
        });
    }

    private void renderHistory() {
        String metric = metricSelector.getValue() == null ? "TEMPERATURE" : metricSelector.getValue();
        chartHeading.setText("LIVE " + metric);
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
        history.forEach(reading -> temperatureChart.addSample(metricValue(reading, metric),
                Instant.ofEpochMilli(reading.getTimestampEpochMs())));
    }

    private static double metricValue(TelemetryReading reading, String metric) {
        return switch (metric) {
            case "VIBRATION" -> reading.getVibrationMmS();
            case "PRESSURE" -> reading.getPressureBar();
            case "ENERGY" -> reading.getEnergyKw();
            default -> reading.getTemperatureC();
        };
    }

    private void loadMachineContext() {
        if (demo) {
            machineContext.setText("DEMO MACHINE · linked incidents and work orders are simulated.");
            return;
        }
        String contextMachineId = activeMachineId;
        MachineDto selected = fleetMachines.stream()
                .filter(machine -> machine.id().equals(contextMachineId)).findFirst().orElse(null);
        String identity = selected == null ? contextMachineId
                : contextMachineId + " · " + nullToDash(selected.status()) + " · "
                + nullToDash(selected.location()) + " · last seen "
                + (selected.lastSeenAt() == null ? "not reported" : TIME_FMT.format(selected.lastSeenAt()));
        machineContext.setText(identity + "\nLoading linked alerts and work orders…");
        rest.getAlertsForMachine(contextMachineId).thenCombine(rest.getWorkOrdersForMachine(contextMachineId),
                (alerts, orders) -> new String[] {
                        Integer.toString(alerts.size()),
                        Long.toString(orders.stream().filter(order -> !"COMPLETED".equalsIgnoreCase(order.status())
                                && !"CANCELLED".equalsIgnoreCase(order.status())).count())
                }).whenComplete((counts, error) -> Platform.runLater(() -> {
            if (!contextMachineId.equals(activeMachineId)) return;
            if (error != null) {
                machineContext.setText(identity + "\nLinked records unavailable · " + error.getMessage());
            } else {
                machineContext.setText(identity + "\nAlerts " + counts[0] + " · Open work orders " + counts[1]);
            }
        }));
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private void onNewAlert(AlertDecisionPayload alert) {
        String bannerText = String.format("NEW %s ANOMALY — %s — observed %.2f (threshold %.2f)",
                alert.severity(), alert.metric(), alert.observedValue(), alert.thresholdValue());
        showAlertBanner(bannerText, alert.severity());
        Platform.runLater(() -> backendInfo.setText("Creating alert on maintenance service…"));

        String sourceEventId = UUID.nameUUIDFromBytes((alert.machineId() + "|"
                + alert.metric().name() + "|" + alert.occurredAt().toEpochMilli())
                .getBytes(StandardCharsets.UTF_8)).toString();
        var request = new CreateAlertRequest(alert.machineId(), alert.metric().name(), alert.severity().name(),
                alert.observedValue(), alert.thresholdValue(), bannerText, alert.occurredAt(), sourceEventId);
        String alertKey = alert.machineId() + "|" + alert.metric().name();
        CompletableFuture<AlertDto> alertCreation = rest.createAlert(request);
        alertCreations.put(alertKey, alertCreation);
        alertCreation.whenComplete((alertDto, error) -> {
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
                        if (stockError != null || stock == null) {
                            Platform.runLater(() -> backendInfo.setText("Stock check failed: "
                                    + (stockError == null ? "empty response" : stockError.getMessage())));
                            return;
                        }
                        if (!stock.isAvailable()) {
                            rest.updateWorkOrderStatus(workOrder.id(), "WAITING_FOR_PARTS")
                                    .whenComplete((waiting, waitingError) -> Platform.runLater(() ->
                                            backendInfo.setText(waitingError == null
                                                    ? "Waiting for parts · " + partId
                                                    : "Stock unavailable; work-order update failed: "
                                                    + waitingError.getMessage())));
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
        String alertKey = alert.machineId() + "|" + alert.metric().name();
        CompletableFuture<AlertDto> pending = alertCreations.get(alertKey);
        CompletableFuture<AlertDto> alertToResolve =
                pending == null
                        ? findOpenAlert(alert)
                        : pending.handle((created, error) -> null)
                                .thenCompose(ignored -> findOpenAlert(alert));

        alertToResolve.thenCompose(active -> active == null
                        ? CompletableFuture.completedFuture(null)
                        : rest.resolveAlert(active.id()))
                .whenComplete((resolved, error) -> {
                    if (pending != null) alertCreations.remove(alertKey, pending);
                    Platform.runLater(() -> backendInfo.setText(error != null
                            ? "Alert recovery update failed: " + error.getMessage()
                            : resolved == null
                                    ? "Telemetry recovered · no persisted alert to resolve"
                                    : "Alert resolved · " + resolved.id()));
                });
    }

    private CompletableFuture<AlertDto> findOpenAlert(AlertDecisionPayload decision) {
        return rest.getAlertsForMachine(decision.machineId()).thenApply(alerts -> alerts.stream()
                .filter(alert -> alert.metricType().equalsIgnoreCase(decision.metric().name()))
                .filter(alert -> "ACTIVE".equalsIgnoreCase(alert.status())
                        || "ACKNOWLEDGED".equalsIgnoreCase(alert.status()))
                .findFirst()
                .orElse(null));
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
            TelemetryReading reading = TelemetryReading.newBuilder().setTemperatureC((float) current[0])
                    .setVibrationMmS((float) current[1]).setPressureBar((float) current[2])
                    .setEnergyKw((float) current[3]).setTimestampEpochMs(System.currentTimeMillis()).build();
            history.addLast(reading);
            while (history.size() > 72) history.removeFirst();
            renderHistory();
            lastUpdate.setText("Simulated reading · " + TIME_FMT.format(Instant.now()));
            Severity severity = Thresholds.defaults().classifyAll(current[0], current[1], current[2], current[3]);
            statusPill.setText(severity == Severity.NORMAL ? "RUNNING" : severity.name());
            statusPill.setState(severity == Severity.NORMAL ? "success" : severity.name().toLowerCase());
            TrafficEventBus.publish("gRPC", "STATE", severity.name());
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
        searchDebounce.stop();
        if (demoReadings != null) demoReadings.stop();
        if (orchestrator != null) orchestrator.close();
        if (grpc != null) grpc.close();
    }
}
