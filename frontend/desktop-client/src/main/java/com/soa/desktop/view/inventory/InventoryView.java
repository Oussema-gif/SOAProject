package com.soa.desktop.view.inventory;

import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.client.soap.generated.CheckStockResponse;
import com.soa.desktop.ui.components.StatusChip;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.UUID;

public final class InventoryView extends VBox {

    public record InventoryRow(String id, String name, String stock, String status) { }

    private final TableView<InventoryRow> table = new TableView<>();
    private final Label statusLabel = new Label("Check spare-part availability via SOAP.");
    private final Label resultLabel = new Label("");
    private final InventorySoapClient soap;
    private final boolean demo;
    private final TextField partId = new TextField("PART-001");
    private final TextField quantity = new TextField("1");
    private final TextField workOrder = new TextField();
    private boolean available;
    private String verifiedPartId;
    private int verifiedQuantity;

    public InventoryView(InventorySoapClient soap, boolean demo) {
        this.soap = soap;
        this.demo = demo;
        getStyleClass().add("page");
        setSpacing(18);
        setPadding(new Insets(8));

        VBox header = new VBox(7);
        Label sectionId = new Label("05 / PARTS & LOGISTICS");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Spare-parts inventory");
        title.getStyleClass().add("page-title");
        statusLabel.getStyleClass().add("body-muted");
        header.getChildren().addAll(sectionId, title, statusLabel);

        VBox controls = new VBox(12);
        controls.getStyleClass().add("surface-card");
        Label formTitle = new Label("AVAILABILITY / RESERVATION");
        formTitle.getStyleClass().add("panel-heading");
        HBox fields = new HBox(10);
        fields.setAlignment(Pos.CENTER_LEFT);
        partId.setPromptText("Part ID");
        quantity.setPromptText("Qty");
        quantity.setPrefWidth(90);
        workOrder.setPromptText("Work order ID");
        partId.textProperty().addListener((observable, oldValue, newValue) -> available = false);
        quantity.textProperty().addListener((observable, oldValue, newValue) -> available = false);
        HBox.setHgrow(workOrder, Priority.ALWAYS);
        Button check = new Button("CHECK STOCK");
        check.getStyleClass().add("primary-button");
        check.setOnAction(event -> checkStock());
        Button reserve = new Button("RESERVE PART");
        reserve.setOnAction(event -> reservePart());
        fields.getChildren().addAll(partId, quantity, workOrder, check, reserve);
        resultLabel.getStyleClass().add("mono-muted");
        controls.getChildren().addAll(formTitle, fields, resultLabel);

        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            TableRow<InventoryRow> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && newValue != oldValue) Motion.enter(row).play();
            });
            return row;
        });
        TableColumn<InventoryRow, String> id = column("PART ID", InventoryRow::id, 160);
        TableColumn<InventoryRow, String> name = column("COMPONENT", InventoryRow::name, 280);
        TableColumn<InventoryRow, String> stock = column("AVAILABLE STOCK", InventoryRow::stock, 160);
        TableColumn<InventoryRow, String> state = column("AVAILABILITY", InventoryRow::status, 160);
        table.getColumns().addAll(id, name, stock, state);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, controls, table);

        if (demo) {
            table.getItems().setAll(
                    new InventoryRow("PART-001", "Thermal sensor", "42", "IN STOCK"),
                    new InventoryRow("PART-008", "Vibration probe", "08", "LOW STOCK"),
                    new InventoryRow("PART-012", "Pressure transducer", "19", "IN STOCK"));
            available = true;
            verifiedPartId = partId.getText().trim();
            verifiedQuantity = Integer.parseInt(quantity.getText().trim());
            resultLabel.setText("DEMO STOCK · PART-001 · 42 available");
            statusLabel.setText("DEMO MODE · SOAP availability and reservation flow simulated");
        } else {
            if (soap == null) {
                statusLabel.setText("SOAP client initializing; use Check Stock when the service link is ready.");
            } else {
                checkStock();
            }
        }
    }

    private TableColumn<InventoryRow, String> column(String title,
                                                      java.util.function.Function<InventoryRow, String> value,
                                                      double width) {
        TableColumn<InventoryRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void checkStock() {
        String id = partId.getText().trim();
        int requested;
        try {
            requested = Integer.parseInt(quantity.getText().trim());
            if (requested < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            resultLabel.setText("Enter a quantity greater than zero.");
            available = false;
            return;
        }
        if (id.isEmpty()) {
            resultLabel.setText("Enter a part ID.");
            available = false;
            return;
        }
        if (demo) {
            TrafficEventBus.publish("SOAP", "REQUEST", "PENDING");
            available = true;
            verifiedPartId = id;
            verifiedQuantity = requested;
            TrafficEventBus.publish("SOAP", "RESPONSE", "SUCCESS");
            resultLabel.setText("DEMO STOCK · " + id + " · 42 available · requested " + requested);
            return;
        }
        if (soap == null) {
            resultLabel.setText("SOAP client is not available.");
            available = false;
            return;
        }
        resultLabel.setText("Checking stock via SOAP…");
        soap.checkStock(id, requested).whenComplete((response, error) -> Platform.runLater(() -> {
            if (error != null) {
                available = false;
                resultLabel.setText("Stock check failed · " + error.getMessage());
                statusLabel.setText("SOAP service unavailable");
                return;
            }
            displayStock(id, requested, response);
        }));
    }

    private void displayStock(String id, int requested, CheckStockResponse response) {
        available = response.isAvailable();
        verifiedPartId = available ? id : null;
        verifiedQuantity = available ? requested : 0;
        String state = available ? "IN STOCK" : "LOW STOCK";
        table.getItems().setAll(new InventoryRow(id, labelFor(id), String.valueOf(response.getAvailableQuantity()), state));
        resultLabel.setText((available ? "AVAILABLE" : "INSUFFICIENT STOCK") + " · "
                + response.getAvailableQuantity() + " units available");
        statusLabel.setText("Stock level fetched from Inventory SOAP service");
    }

    private void reservePart() {
        String id = partId.getText().trim();
        String orderId = workOrder.getText().trim();
        int requested;
        try {
            requested = Integer.parseInt(quantity.getText().trim());
            if (requested < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            resultLabel.setText("Enter a valid reservation quantity.");
            return;
        }
        if (!available || !id.equals(verifiedPartId) || requested != verifiedQuantity) {
            resultLabel.setText("Check stock and confirm availability before reserving.");
            return;
        }
        if (orderId.isEmpty()) {
            resultLabel.setText("Enter a work order ID to link the reservation.");
            return;
        }
        if (demo) {
            TrafficEventBus.publish("SOAP", "REQUEST", "PENDING");
            resultLabel.setText("DEMO RESERVATION · " + UUID.randomUUID().toString().substring(0, 8)
                    + " · " + id + " × " + requested + " · linked to " + orderId);
            TrafficEventBus.publish("SOAP", "RESPONSE", "SUCCESS");
            available = false;
            return;
        }
        if (soap == null) {
            resultLabel.setText("SOAP client is not available.");
            return;
        }
        resultLabel.setText("Reserving " + id + "…");
        soap.reservePart(id, requested, UUID.randomUUID().toString(), orderId)
                .whenComplete((reservation, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        resultLabel.setText("Reservation failed · " + error.getMessage());
                        return;
                    }
                    resultLabel.setText("Reserved · " + reservation.getReservationId() + " · linked to " + orderId);
                    statusLabel.setText("Reservation confirmed by Inventory SOAP service");
                    available = false;
                }));
    }

    private static String labelFor(String partId) {
        return switch (partId) {
            case "PART-001" -> "Thermal sensor";
            case "PART-008" -> "Vibration probe";
            case "PART-012" -> "Pressure transducer";
            default -> "Industrial spare part";
        };
    }
}
