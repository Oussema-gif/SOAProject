package com.soa.desktop.view.alerts;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.AlertDto;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AlertsView extends VBox {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MaintenanceRestClient rest;
    private final TableView<AlertDto> table = new TableView<>();
    private final Label statusLabel = new Label("Loading register…");

    public AlertsView(MaintenanceRestClient rest) {
        this.rest = rest;
        setSpacing(24);

        // Editorial Header
        VBox header = new VBox(8);
        Label sectionId = new Label("03 / INCIDENTS");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Alert Register");
        title.getStyleClass().add("editorial-title");
        statusLabel.getStyleClass().add("mono-muted");
        header.getChildren().addAll(sectionId, title, statusLabel);

        // Technical Data Table
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AlertDto, String> colId = new TableColumn<>("ALERT ID");
        colId.setCellValueFactory(d -> new SimpleStringProperty(shortId(d.getValue().id())));
        colId.setPrefWidth(120);

        TableColumn<AlertDto, String> colMachine = new TableColumn<>("MACHINE");
        colMachine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().machineId()));
        colMachine.setPrefWidth(150);

        TableColumn<AlertDto, String> colMetric = new TableColumn<>("METRIC");
        colMetric.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().metricType()));

        TableColumn<AlertDto, String> colSeverity = new TableColumn<>("SEVERITY");
        colSeverity.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().severity()));
        colSeverity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    getStyleClass().removeAll("status-critical", "status-warning", "status-success");
                    if ("CRITICAL".equals(item)) getStyleClass().add("status-critical");
                    else if ("WARNING".equals(item)) getStyleClass().add("status-warning");
                    else getStyleClass().add("status-success");
                }
            }
        });

        TableColumn<AlertDto, String> colObserved = new TableColumn<>("OBSERVED");
        colObserved.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f", d.getValue().observedValue())));

        TableColumn<AlertDto, String> colTime = new TableColumn<>("TIMESTAMP");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(TIME_FMT.format(d.getValue().occurredAt())));

        table.getColumns().addAll(colId, colMachine, colMetric, colSeverity, colObserved, colTime);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, table);

        loadData();
    }

    private String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) + "..." : id;
    }

    private void loadData() {
        rest.getActiveAlerts().whenComplete((alerts, err) -> Platform.runLater(() -> {
            if (err != null) {
                statusLabel.setText("Error: " + err.getMessage());
                return;
            }
            table.getItems().setAll(alerts);
            statusLabel.setText("Showing " + alerts.size() + " active incidents.");
        }));
    }
}