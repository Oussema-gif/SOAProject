package com.soa.desktop.view.inventory;

import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.client.soap.generated.CheckStockResponse;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class InventoryView extends VBox {

    // We use a record to hold exactly what we need for the UI
    public record InventoryRow(String id, String name, String stock, String status) {}

    private final TableView<InventoryRow> table = new TableView<>();
    private final Label statusLabel = new Label("Checking stock via SOAP…");
    private final InventorySoapClient soap;

    public InventoryView(InventorySoapClient soap) {
        this.soap = soap;
        setSpacing(24);

        VBox header = new VBox(8);
        Label sectionId = new Label("05 / INVENTORY");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Spare Parts Ledger");
        title.getStyleClass().add("editorial-title");
        statusLabel.getStyleClass().add("mono-muted");
        header.getChildren().addAll(sectionId, title, statusLabel);

        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<InventoryRow, String> colId = new TableColumn<>("PART ID");
        colId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().id()));
        colId.setPrefWidth(150);

        TableColumn<InventoryRow, String> colName = new TableColumn<>("NAME");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        colName.setPrefWidth(300);

        TableColumn<InventoryRow, String> colStock = new TableColumn<>("AVAILABLE STOCK");
        colStock.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().stock()));

        TableColumn<InventoryRow, String> colStatus = new TableColumn<>("STATUS");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));

        table.getColumns().addAll(colId, colName, colStock, colStatus);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, table);

        loadInventory();
    }

    private void loadInventory() {
        // We are using the CheckStock operation since we know it exists
        // and it returns the actual stock quantities.
        soap.checkStock("PART-001", 1).whenComplete((resp, err) -> {
            Platform.runLater(() -> {
                if (err != null) {
                    statusLabel.setText("SOAP Error: " + err.getMessage());
                    return;
                }

                String status = resp.isAvailable() ? "IN STOCK" : "LOW STOCK";
                table.getItems().add(new InventoryRow(
                        "PART-001",
                        "Thermal Sensor",
                        String.valueOf(resp.getAvailableQuantity()),
                        status
                ));

                statusLabel.setText("Showing 1 part (fetched via SOAP CheckStock).");
            });
        });
    }
}