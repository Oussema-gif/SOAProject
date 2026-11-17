package com.soa.desktop.view.alerts;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.AlertDto;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AlertsView extends VBox {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    private final MaintenanceRestClient rest;
    private final boolean demo;
    private final TableView<AlertDto> table = new TableView<>();
    private final Label statusLabel = new Label("Loading active incidents…");

    public AlertsView(MaintenanceRestClient rest, boolean demo) {
        this.rest = rest;
        this.demo = demo;
        getStyleClass().add("page");
        setSpacing(18);
        setPadding(new Insets(8));

        VBox header = new VBox(7);
        Label sectionId = new Label("03 / INCIDENT RESPONSE");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Alert register");
        title.getStyleClass().add("page-title");
        statusLabel.getStyleClass().add("body-muted");
        header.getChildren().addAll(sectionId, title, statusLabel);

        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            TableRow<AlertDto> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && newValue != oldValue) Motion.enter(row).play();
            });
            return row;
        });
        createColumns();
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, table);
        loadData();
    }

    private void createColumns() {
        TableColumn<AlertDto, String> id = column("ALERT ID", alert -> shortId(alert.id()), 120);
        TableColumn<AlertDto, String> machine = column("MACHINE", AlertDto::machineId, 135);
        TableColumn<AlertDto, String> metric = column("METRIC", AlertDto::metricType, 120);
        TableColumn<AlertDto, String> severity = column("SEVERITY", AlertDto::severity, 100);
        severity.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(CRITICAL, !empty && "CRITICAL".equalsIgnoreCase(item));
                pseudoClassStateChanged(WARNING, !empty && "WARNING".equalsIgnoreCase(item));
                pseudoClassStateChanged(SUCCESS, !empty && "NORMAL".equalsIgnoreCase(item));
                setText(empty ? null : item);
            }
        });
        TableColumn<AlertDto, String> observed = column("OBSERVED", alert -> String.format("%.2f", alert.observedValue()), 100);
        TableColumn<AlertDto, String> timestamp = column("TIMESTAMP",
                alert -> alert.occurredAt() == null ? "—" : TIME_FMT.format(alert.occurredAt()), 180);
        TableColumn<AlertDto, String> state = column("STATE", AlertDto::status, 115);

        TableColumn<AlertDto, AlertDto> action = new TableColumn<>("ACTION");
        action.setCellValueFactory(value -> new javafx.beans.property.SimpleObjectProperty<>(value.getValue()));
        action.setCellFactory(column -> new TableCell<>() {
            private final Button acknowledge = new Button("ACKNOWLEDGE");

            {
                acknowledge.getStyleClass().add("small-button");
                acknowledge.setOnAction(event -> acknowledgeAlert(getItem()));
            }

            @Override
            protected void updateItem(AlertDto alert, boolean empty) {
                super.updateItem(alert, empty);
                setGraphic(empty || alert == null || !"ACTIVE".equalsIgnoreCase(alert.status())
                        ? null : acknowledge);
                setText(null);
            }
        });
        table.getColumns().addAll(id, machine, metric, severity, observed, timestamp, state, action);
    }

    private TableColumn<AlertDto, String> column(String title,
                                                  java.util.function.Function<AlertDto, String> value,
                                                  double width) {
        TableColumn<AlertDto, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void acknowledgeAlert(AlertDto alert) {
        if (alert == null) return;
        if (demo) {
            TrafficEventBus.publish("REST", "REQUEST", "PENDING");
            AlertDto acknowledged = new AlertDto(alert.id(), alert.machineId(), alert.metricType(), alert.severity(),
                    "ACKNOWLEDGED", alert.observedValue(), alert.thresholdValue(), alert.occurredAt(),
                    alert.createdAt(), alert.message());
            int index = table.getItems().indexOf(alert);
            if (index >= 0) table.getItems().set(index, acknowledged);
            TrafficEventBus.publish("REST", "RESPONSE", "SUCCESS");
            statusLabel.setText("DEMO MODE · acknowledgement simulated");
            return;
        }
        statusLabel.setText("Acknowledging alert " + shortId(alert.id()) + "…");
        rest.acknowledgeAlert(alert.id()).whenComplete((updated, error) -> Platform.runLater(() -> {
            if (error != null) {
                statusLabel.setText("Acknowledgement failed · " + error.getMessage());
                return;
            }
            statusLabel.setText("Alert acknowledged · refreshing active register");
            loadData();
        }));
    }

    private void loadData() {
        if (demo) {
            table.getItems().setAll(List.of(
                    new AlertDto("ALT-7A31C9", "MACHINE-003", "TEMPERATURE", "CRITICAL", "ACTIVE",
                            91.8, 85, Instant.now().minusSeconds(95), Instant.now().minusSeconds(92), "Thermal envelope exceeded"),
                    new AlertDto("ALT-2F10B4", "MACHINE-002", "VIBRATION", "WARNING", "ACTIVE",
                            5.2, 4, Instant.now().minusSeconds(340), Instant.now().minusSeconds(330), "Vibration trending above limit"),
                    new AlertDto("ALT-8C04D2", "MACHINE-001", "PRESSURE", "WARNING", "ACKNOWLEDGED",
                            6.4, 6, Instant.now().minusSeconds(1200), Instant.now().minusSeconds(1180), "Pressure oscillation observed")));
            statusLabel.setText("DEMO MODE · 2 active alerts · actions simulated");
            return;
        }
        rest.getActiveAlerts().whenComplete((alerts, error) -> Platform.runLater(() -> {
            if (error != null) {
                statusLabel.setText("Could not load active incidents · " + error.getMessage());
                return;
            }
            table.getItems().setAll(alerts);
            statusLabel.setText("Showing " + alerts.size() + " active incidents");
        }));
    }

    private static String shortId(String id) {
        return id != null && id.length() > 8 ? id.substring(0, 8) + "…" : id;
    }
}
