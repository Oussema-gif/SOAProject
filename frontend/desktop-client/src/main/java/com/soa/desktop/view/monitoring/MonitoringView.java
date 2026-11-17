package com.soa.desktop.view.monitoring;

import com.soa.desktop.client.grpc.GrpcTelemetryClient;
import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.CreateAlertRequest;
import com.soa.desktop.client.rest.dto.CreateWorkOrderRequest;
import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.orchestration.AlertDecisionPayload;
import com.soa.desktop.orchestration.AnomalyWorkflowOrchestrator;
import com.soa.desktop.util.ActivityLogger;
import com.soa.telemetry.TelemetryReading;
import com.soa.telemetry.anomaly.Severity;
import com.soa.telemetry.anomaly.Thresholds;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class MonitoringView extends VBox {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final GrpcTelemetryClient grpc;
    private final MaintenanceRestClient rest;
    private final InventorySoapClient soap;
    private final AnomalyWorkflowOrchestrator orchestrator;
    private final String machineId;

    private boolean loggedFirstReading = false;

    private final Label tempValue   = new Label("—");
    private final Label vibValue    = new Label("—");
    private final Label presValue   = new Label("—");
    private final Label energyValue = new Label("—");
    private final Label statusPill  = new Label("CONNECTING");
    private final Label lastUpdate  = new Label("waiting for first reading…");
    private final Label alertBanner = new Label("");
    private final Label backendInfo = new Label("");

    public MonitoringView(String grpcHost, int grpcPort, String machineId,
                          MaintenanceRestClient rest, InventorySoapClient soap) {
        this.machineId = machineId;
        this.rest = rest;
        this.soap = soap;
        this.grpc = new GrpcTelemetryClient(grpcHost, grpcPort);

        ActivityLogger.log("gRPC", "StreamMachineTelemetry", "PENDING", "Connecting to " + machineId);

        this.orchestrator = new AnomalyWorkflowOrchestrator(
                grpc, Thresholds.defaults(), machineId,
                this::onReading, this::onNewAlert, this::onCleared, this::onConnectionError);

        buildLayout();
        orchestrator.start();
    }

    private void buildLayout() {
        setSpacing(32);

        VBox header = new VBox(8);
        Label sectionId = new Label("02 / MACHINES");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Technical Inspection — " + machineId);
        title.getStyleClass().add("editorial-title");

        HBox titleRow = new HBox(16, title, new Region(), statusPill);
        HBox.setHgrow(titleRow.getChildren().get(1), Priority.ALWAYS);
        statusPill.getStyleClass().add("mono-text");

        header.getChildren().addAll(sectionId, titleRow);

        alertBanner.getStyleClass().add("panel");
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
        alertBanner.setMaxWidth(Double.MAX_VALUE);
        alertBanner.setPadding(new Insets(16));

        HBox cards = new HBox(24,
                metricCard("TEMPERATURE", tempValue, "°C"),
                metricCard("VIBRATION",   vibValue, "mm/s"),
                metricCard("PRESSURE",    presValue, "bar"),
                metricCard("ENERGY",      energyValue, "kW"));

        VBox footer = new VBox(8, lastUpdate, backendInfo);
        footer.getStyleClass().add("mono-muted");

        getChildren().addAll(header, alertBanner, cards, footer);
    }

    private void onReading(TelemetryReading r) {
        if (!loggedFirstReading) {
            ActivityLogger.log("gRPC", "StreamMachineTelemetry", "CONNECTED", "Stream active");
            loggedFirstReading = true;
        }
        Platform.runLater(() -> {
            tempValue.setText(String.format("%.2f", r.getTemperatureC()));
            vibValue.setText(String.format("%.2f", r.getVibrationMmS()));
            presValue.setText(String.format("%.2f", r.getPressureBar()));
            energyValue.setText(String.format("%.2f", r.getEnergyKw()));
            lastUpdate.setText("Last reading: " + TIME_FMT.format(Instant.ofEpochMilli(r.getTimestampEpochMs())));

            var sev = classifyQuick(r);
            statusPill.setText(sev);
            statusPill.getStyleClass().removeAll("status-success", "status-warning", "status-critical", "status-offline");
            statusPill.getStyleClass().add(switch (sev) {
                case "CRITICAL" -> "status-critical";
                case "WARNING"  -> "status-warning";
                default         -> "status-success";
            });
        });
    }

    private void onNewAlert(AlertDecisionPayload a) {
        String bannerText = String.format("NEW %s ANOMALY — %s — observed %.2f (threshold %.2f)",
                a.severity(), a.metric(), a.observedValue(), a.thresholdValue());

        showAlertBanner(bannerText, a.severity());
        Platform.runLater(() -> backendInfo.setText("Creating alert on backend…"));

        var req = new CreateAlertRequest(a.machineId(), a.metric().name(), a.severity().name(),
                a.observedValue(), a.thresholdValue(), bannerText, a.occurredAt());

        rest.createAlert(req).whenComplete((alertDto, err) -> {
            if (err != null) {
                Platform.runLater(() -> backendInfo.setText("Alert creation failed: " + err.getMessage()));
                return;
            }
            Platform.runLater(() -> backendInfo.setText("Alert persisted — id " + alertDto.id() + ". Creating WO…"));

            var woReq = new CreateWorkOrderRequest(a.machineId(), alertDto.id(),
                    "Inspect " + a.metric().name().toLowerCase() + " anomaly", bannerText,
                    a.severity() == Severity.CRITICAL ? "HIGH" : "MEDIUM");

            rest.createWorkOrder(woReq).whenComplete((woDto, woErr) -> {
                if (woErr != null) {
                    Platform.runLater(() -> backendInfo.setText("WO failed: " + woErr.getMessage()));
                    return;
                }
                Platform.runLater(() -> backendInfo.setText("WO created. Moving to CHECKING_INVENTORY…"));

                rest.updateWorkOrderStatus(woDto.id(), "CHECKING_INVENTORY").whenComplete((checkingWo, statusErr) -> {
                    if (statusErr != null) {
                        Platform.runLater(() -> backendInfo.setText("Status failed: " + statusErr.getMessage()));
                        return;
                    }
                    Platform.runLater(() -> backendInfo.setText("Checking stock via SOAP…"));

                    String partId = "PART-001";
                    int qty = 1;
                    soap.checkStock(partId, qty).whenComplete((stockResp, soapErr) -> {
                        if (soapErr != null || !stockResp.isAvailable()) {
                            Platform.runLater(() -> backendInfo.setText("Stock check failed."));
                            return;
                        }

                        String requestId = UUID.randomUUID().toString();
                        soap.reservePart(partId, qty, requestId, woDto.id()).whenComplete((resResp, resErr) -> {
                            if (resErr != null) {
                                Platform.runLater(() -> backendInfo.setText("Reservation failed: " + resErr.getMessage()));
                                return;
                            }
                            Platform.runLater(() -> backendInfo.setText("Reserved. Linking to WO…"));

                            rest.associateReservation(woDto.id(), resResp.getReservationId(), partId, qty).whenComplete((finalWo, linkErr) -> {
                                if (linkErr != null) {
                                    Platform.runLater(() -> backendInfo.setText("Link failed: " + linkErr.getMessage()));
                                } else {
                                    Platform.runLater(() -> backendInfo.setText("SUCCESS: Alert → WO → Reserved (" + resResp.getReservationId() + ")"));
                                }
                            });
                        });
                    });
                });
            });
        });
    }

    private void onCleared(AlertDecisionPayload a) {
        String text = String.format("CLEARED — %s returned to normal (was %s)", a.metric(), a.severity());
        showAlertBanner(text, null);
    }

    private void onConnectionError(Throwable t) {
        ActivityLogger.log("gRPC", "StreamMachineTelemetry", "FAILED", t.getMessage());
        Platform.runLater(() -> {
            statusPill.setText("DISCONNECTED");
            statusPill.getStyleClass().removeAll("status-success", "status-warning", "status-critical", "status-offline");
            statusPill.getStyleClass().add("status-offline");
            lastUpdate.setText("Stream error: " + t.getMessage());
        });
    }

    private void showAlertBanner(String text, Severity severity) {
        Platform.runLater(() -> {
            alertBanner.setText(text);
            alertBanner.setStyle("-fx-font-family: 'IBM Plex Mono'; -fx-font-size: 14px;");

            if (severity == Severity.CRITICAL) {
                alertBanner.setStyle(alertBanner.getStyle() + "; -fx-text-fill: #B64236; -fx-border-color: #B64236;");
            } else if (severity == Severity.WARNING) {
                alertBanner.setStyle(alertBanner.getStyle() + "; -fx-text-fill: #B57A27; -fx-border-color: #B57A27;");
            } else {
                alertBanner.setStyle(alertBanner.getStyle() + "; -fx-text-fill: #397B59; -fx-border-color: #397B59;");
            }

            alertBanner.setVisible(true);
            alertBanner.setManaged(true);
        });
    }

    private static String classifyQuick(TelemetryReading r) {
        if (r.getTemperatureC() > 85 || r.getVibrationMmS() > 7 || r.getPressureBar() > 7 || r.getEnergyKw() > 15) return "CRITICAL";
        if (r.getTemperatureC() > 75 || r.getVibrationMmS() > 4 || r.getPressureBar() > 6 || r.getEnergyKw() > 12) return "WARNING";
        return "RUNNING";
    }

    private VBox metricCard(String name, Label valueLabel, String unit) {
        var card = new VBox(12);
        card.getStyleClass().add("panel");
        card.setPrefWidth(220);
        var n = new Label(name); n.getStyleClass().add("section-label");
        valueLabel.getStyleClass().add("editorial-title");
        valueLabel.setStyle("-fx-font-size: 42px;");
        var u = new Label(unit); u.getStyleClass().add("mono-muted");
        card.getChildren().addAll(n, valueLabel, u);
        return card;
    }

    public void dispose() {
        orchestrator.close();
        grpc.close();
    }
}