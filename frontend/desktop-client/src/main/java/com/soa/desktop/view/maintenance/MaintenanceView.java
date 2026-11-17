package com.soa.desktop.view.maintenance;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.WorkOrderDto;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MaintenanceView extends VBox {

    private final MaintenanceRestClient rest;
    private final TableView<WorkOrderDto> table = new TableView<>();
    private final Label statusLabel = new Label("Loading work orders…");

    public MaintenanceView(MaintenanceRestClient rest) {
        this.rest = rest;
        setSpacing(24);

        VBox header = new VBox(8);
        Label sectionId = new Label("04 / DISPATCH");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Maintenance Work Orders");
        title.getStyleClass().add("editorial-title");
        statusLabel.getStyleClass().add("mono-muted");
        header.getChildren().addAll(sectionId, title, statusLabel);

        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<WorkOrderDto, String> colId = new TableColumn<>("WO ID");
        colId.setCellValueFactory(d -> new SimpleStringProperty(shortId(d.getValue().id())));
        colId.setPrefWidth(100);

        TableColumn<WorkOrderDto, String> colMachine = new TableColumn<>("MACHINE");
        colMachine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().machineId()));

        TableColumn<WorkOrderDto, String> colTitle = new TableColumn<>("DESCRIPTION");
        colTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().title()));
        colTitle.setPrefWidth(300);

        TableColumn<WorkOrderDto, String> colPriority = new TableColumn<>("PRIORITY");
        colPriority.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().priority()));
        colPriority.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    getStyleClass().removeAll("status-critical", "status-warning", "status-success");
                    if ("HIGH".equals(item)) getStyleClass().add("status-critical");
                    else if ("MEDIUM".equals(item)) getStyleClass().add("status-warning");
                    else getStyleClass().add("status-success");
                }
            }
        });

        TableColumn<WorkOrderDto, String> colStatus = new TableColumn<>("STATUS");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));

        table.getColumns().addAll(colId, colMachine, colTitle, colPriority, colStatus);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, table);

        loadData();
    }

    private String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) + "..." : id;
    }

    private void loadData() {
        rest.getWorkOrders().whenComplete((wos, err) -> Platform.runLater(() -> {
            if (err != null) {
                statusLabel.setText("Error: " + err.getMessage());
                return;
            }
            table.getItems().setAll(wos);
            statusLabel.setText("Showing " + wos.size() + " dispatched work orders.");
        }));
    }
}