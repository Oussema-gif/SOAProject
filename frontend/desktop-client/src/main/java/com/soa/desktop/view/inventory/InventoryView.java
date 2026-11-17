package com.soa.desktop.view.inventory;

import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.client.soap.generated.CheckStockResponse;
import com.soa.desktop.ui.components.StatusChip;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
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

    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    public record InventoryRow(String id, String sku, String name, String stock, String minimum, String unit, String status) { }

    private final TableView<InventoryRow> table = new TableView<>();
    private final Label statusLabel = new Label("Look up a part ID; the SOAP contract does not expose a parts-list operation.");
    private final Label resultLabel = new Label("");
    private final InventorySoapClient soap;
    private final boolean demo;
    private final TextField partId = new TextField();
    private final TextField quantity = new TextField("1");
    private final TextField workOrder = new TextField();
    private final Button check = new Button("CHECK STOCK");
    private final Button reserve = new Button("RESERVE PART");
    private boolean available;
    private String verifiedPartId;
    private int verifiedQuantity;

    public InventoryView(InventorySoapClient soap, boolean demo) {
        this(soap, demo, null);
    }

    public InventoryView(InventorySoapClient soap, boolean demo, String initialPartId) {
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
        if (initialPartId != null) partId.setText(initialPartId);
        quantity.setPromptText("Qty");
        quantity.setPrefWidth(90);
        workOrder.setPromptText("Work order ID");
        partId.textProperty().addListener((observable, oldValue, newValue) -> invalidateStockCheck());
        quantity.textProperty().addListener((observable, oldValue, newValue) -> invalidateStockCheck());
        HBox.setHgrow(workOrder, Priority.ALWAYS);
        check.getStyleClass().add("primary-button");
        check.setOnAction(event -> checkStock());
        reserve.setOnAction(event -> reservePart());
        fields.getChildren().addAll(partId, quantity, workOrder, check, reserve);
        reserve.setDisable(true);
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
        TableColumn<InventoryRow, String> id = column("PART ID", InventoryRow::id, 130);
        TableColumn<InventoryRow, String> sku = column("SKU", InventoryRow::sku, 150);
        TableColumn<InventoryRow, String> name = column("COMPONENT", InventoryRow::name, 240);
        TableColumn<InventoryRow, String> stock = column("ON HAND", InventoryRow::stock, 100);
        TableColumn<InventoryRow, String> minimum = column("MINIMUM", InventoryRow::minimum, 100);
        TableColumn<InventoryRow, String> unit = column("UNIT", InventoryRow::unit, 80);
        TableColumn<InventoryRow, String> state = column("AVAILABILITY", InventoryRow::status, 140);
        state.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(CRITICAL, !empty && "INSUFFICIENT".equalsIgnoreCase(item));
                pseudoClassStateChanged(WARNING, !empty && "LOW STOCK".equalsIgnoreCase(item));
                pseudoClassStateChanged(SUCCESS, !empty && "IN STOCK".equalsIgnoreCase(item));
                setText(empty ? null : item);
            }
        });
        table.getColumns().addAll(id, sku, name, stock, minimum, unit, state);
        table.setPlaceholder(new Label("No part loaded · enter a part ID and check its live stock."));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, controls, table);

        if (demo) {
            table.getItems().setAll(
                    new InventoryRow("PART-001", "DEMO-SKU-001", "Industrial bearing", "42", "10", "PCS", "IN STOCK"));
            available = true;
            reserve.setDisable(false);
            if (partId.getText().isBlank()) partId.setText("PART-001");
            verifiedPartId = partId.getText().trim();
            verifiedQuantity = Integer.parseInt(quantity.getText().trim());
            resultLabel.setText("DEMO STOCK · PART-001 · available 42 · minimum 10");
            statusLabel.setText("DEMO MODE · stock and reservation feedback are simulated");
        } else {
            if (initialPartId != null) checkStock();
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
            reserve.setDisable(true);
            return;
        }
        if (id.isEmpty()) {
            resultLabel.setText("Enter a part ID.");
            available = false;
            reserve.setDisable(true);
            return;
        }
        if (demo) {
            TrafficEventBus.publish("SOAP", "REQUEST", "PENDING");
            available = true;
            verifiedPartId = id;
            verifiedQuantity = requested;
            reserve.setDisable(false);
            TrafficEventBus.publish("SOAP", "RESPONSE", "SUCCESS");
            resultLabel.setText("DEMO STOCK · " + id + " · 42 available · requested " + requested);
            return;
        }
        if (soap == null) {
            resultLabel.setText("SOAP client is not available.");
            available = false;
            reserve.setDisable(true);
            return;
        }
        setBusy(true);
        resultLabel.setText("Reading part details and checking stock via authenticated SOAP…");
        soap.getSparePart(id).thenCompose(part -> soap.checkStock(id, requested)
                .thenApply(stock -> new StockResult(part, stock)))
                .whenComplete((response, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                available = false;
                resultLabel.setText("Stock check failed · " + error.getMessage());
                statusLabel.setText("Authenticated SOAP request failed");
                return;
            }
            displayStock(id, requested, response);
        }));
    }

    private record StockResult(com.soa.desktop.client.soap.generated.GetSparePartResponse part,
                               CheckStockResponse stock) { }

    private void displayStock(String id, int requested, StockResult response) {
        CheckStockResponse stock = response.stock();
        var part = response.part();
        available = stock.isAvailable();
        verifiedPartId = available ? id : null;
        verifiedQuantity = available ? requested : 0;
        reserve.setDisable(!available);
        String state = !available ? "INSUFFICIENT" : part.getAvailableQuantity() <= part.getMinimumStockLevel()
                ? "LOW STOCK" : "IN STOCK";
        table.getItems().setAll(new InventoryRow(part.getPartId(), part.getSku(), part.getName(),
                String.valueOf(part.getAvailableQuantity()), String.valueOf(part.getMinimumStockLevel()),
                part.getUnit(), state));
        resultLabel.setText("SOAP RESPONSE · " + stock.getMessage() + " · requested " + requested
                + " · available " + stock.getAvailableQuantity());
        statusLabel.setText("Part record and stock availability confirmed by Inventory SOAP service");
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
            reserve.setDisable(true);
            reserve.setDisable(true);
            return;
        }
        if (soap == null) {
            resultLabel.setText("SOAP client is not available.");
            return;
        }
        setBusy(true);
        resultLabel.setText("Reserving " + id + "…");
        soap.reservePart(id, requested, UUID.randomUUID().toString(), orderId)
                .whenComplete((reservation, error) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (error != null) {
                        resultLabel.setText("Reservation failed · " + error.getMessage());
                        return;
                    }
                    if (reservation.getReservationId() == null || reservation.getReservationId().isBlank()) {
                        resultLabel.setText("SOAP returned no reservation ID · reservation is unconfirmed");
                        return;
                    }
                    resultLabel.setText("SOAP RESPONSE · " + reservation.getStatus() + " · "
                            + reservation.getReservationId() + " · qty " + reservation.getReservedQuantity()
                            + " · " + reservation.getTimestamp() + " · work order " + orderId);
                    statusLabel.setText("Reservation confirmed by Inventory SOAP service");
                    available = false;
                    reserve.setDisable(true);
                }));
    }

    private void invalidateStockCheck() {
        available = false;
        verifiedPartId = null;
        verifiedQuantity = 0;
        reserve.setDisable(true);
        if (!demo) table.getItems().clear();
    }

    private void setBusy(boolean busy) {
        check.setDisable(busy);
        reserve.setDisable(busy || !available);
        partId.setDisable(busy);
        quantity.setDisable(busy);
        workOrder.setDisable(busy);
    }
}
