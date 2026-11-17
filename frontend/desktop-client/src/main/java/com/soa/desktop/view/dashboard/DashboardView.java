package com.soa.desktop.view.dashboard;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class DashboardView extends VBox {

    private final MaintenanceRestClient rest;
    private final Label alertCount = new Label("—");
    private final Label workOrderCount = new Label("—");

    public DashboardView(MaintenanceRestClient rest, Runnable onViewAlerts) {
        this.rest = rest;

        setSpacing(32);

        // 1. Editorial Page Header
        VBox header = new VBox(8);
        Label sectionId = new Label("01 / OPERATIONS");
        sectionId.getStyleClass().add("section-label");

        Label title = new Label("System Telemetry");
        title.getStyleClass().add("editorial-title");

        Label description = new Label("Real-time overview of the industrial monitoring environment.");
        description.getStyleClass().add("mono-muted");

        header.getChildren().addAll(sectionId, title, description);

        // 2. Asymmetric Grid Layout
        GridPane grid = new GridPane();
        grid.setHgap(32);
        grid.setVgap(32);

        // Left Column (Wide) - Machine Status Ledger
        VBox ledgerPanel = new VBox(16);
        ledgerPanel.getStyleClass().add("panel");

        Label ledgerTitle = new Label("MACHINE STATUS LEDGER");
        ledgerTitle.getStyleClass().add("section-label");

        VBox rows = new VBox(0);
        rows.getChildren().addAll(
                createMachineRow("MACHINE-001", "RUNNING", "status-success", "55.2 °C"),
                createMachineRow("MACHINE-002", "WARNING", "status-warning", "78.4 °C"),
                createMachineRow("MACHINE-003", "CRITICAL", "status-critical", "92.1 °C"),
                createMachineRow("MACHINE-004", "OFFLINE", "status-offline", "—")
        );
        ledgerPanel.getChildren().addAll(ledgerTitle, rows);

        // Right Column (Narrow) - High-level metrics
        VBox metricsPanel = new VBox(32);

        VBox m1 = createMetricBlock("ACTIVE ALERTS", alertCount, onViewAlerts);
        VBox m2 = createMetricBlock("OPEN WORK ORDERS", workOrderCount, null);

        metricsPanel.getChildren().addAll(m1, m2);

        // Add to Grid
        grid.add(ledgerPanel, 0, 0);
        grid.add(metricsPanel, 1, 0);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(65);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(35);
        grid.getColumnConstraints().addAll(col1, col2);

        getChildren().addAll(header, grid);
        loadData();
    }

    private VBox createMetricBlock(String title, Label valueLabel, Runnable onClick) {
        VBox box = new VBox(8);
        box.setStyle("-fx-border-color: #272B2A; -fx-border-width: 1 0 0 0; -fx-padding: 16 0 0 0;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-label");

        valueLabel.getStyleClass().add("editorial-title");
        valueLabel.setStyle("-fx-font-size: 48px; -fx-text-fill: #A64E37;");

        box.getChildren().addAll(titleLabel, valueLabel);

        if (onClick != null) {
            Label link = new Label("View details →");
            link.getStyleClass().add("mono-text");
            link.setStyle("-fx-text-fill: #A64E37; -fx-cursor: hand;");
            link.setOnMouseClicked(e -> onClick.run());
            box.getChildren().add(link);
        }
        return box;
    }

    private HBox createMachineRow(String id, String status, String statusCss, String reading) {
        HBox row = new HBox();
        row.setPadding(new Insets(16, 8, 16, 8));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-border-color: transparent transparent #D1CDC4 transparent; -fx-border-width: 0 0 1 0;");

        Label idLabel = new Label(id);
        idLabel.getStyleClass().add("mono-text");
        idLabel.setPrefWidth(200);

        Label statusLabel = new Label(status);
        statusLabel.getStyleClass().addAll("mono-text", statusCss);
        statusLabel.setPrefWidth(120);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label readingLabel = new Label(reading);
        readingLabel.getStyleClass().add("mono-text");

        row.getChildren().addAll(idLabel, statusLabel, spacer, readingLabel);

        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #D1CDC4 transparent; -fx-border-width: 0 0 1 0;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent #D1CDC4 transparent; -fx-border-width: 0 0 1 0;"));
        return row;
    }

    private void loadData() {
        rest.getActiveAlerts().whenComplete((alerts, err) -> Platform.runLater(() -> {
            alertCount.setText(err == null ? String.valueOf(alerts.size()) : "ERR");
        }));
        rest.getWorkOrders().whenComplete((wos, err) -> Platform.runLater(() -> {
            workOrderCount.setText(err == null ? String.valueOf(wos.size()) : "ERR");
        }));
    }
}