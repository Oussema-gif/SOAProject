package com.soa.desktop.view.maintenance;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.WorkOrderDto;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MaintenanceView extends VBox {

    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "OPEN", List.of("CHECKING_INVENTORY", "CANCELLED"),
            "CHECKING_INVENTORY", List.of("PENDING_RESERVATION", "WAITING_FOR_PARTS", "READY_FOR_MAINTENANCE", "CANCELLED"),
            "PENDING_RESERVATION", List.of("READY_FOR_MAINTENANCE", "WAITING_FOR_PARTS", "CANCELLED"),
            "WAITING_FOR_PARTS", List.of("CHECKING_INVENTORY", "CANCELLED"),
            "READY_FOR_MAINTENANCE", List.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", List.of("COMPLETED", "CANCELLED"),
            "COMPLETED", List.of(),
            "CANCELLED", List.of());

    private final MaintenanceRestClient rest;
    private final boolean demo;
    private final TableView<WorkOrderDto> table = new TableView<>();
    private final Label statusLabel = new Label("Loading work orders…");

    public MaintenanceView(MaintenanceRestClient rest, boolean demo) {
        this.rest = rest;
        this.demo = demo;
        getStyleClass().add("page");
        setSpacing(18);
        setPadding(new Insets(8));

        VBox header = new VBox(7);
        Label sectionId = new Label("04 / DISPATCH");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Maintenance work orders");
        title.getStyleClass().add("page-title");
        statusLabel.getStyleClass().add("body-muted");
        header.getChildren().addAll(sectionId, title, statusLabel);

        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            TableRow<WorkOrderDto> row = new TableRow<>();
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
        TableColumn<WorkOrderDto, String> id = column("WORK ORDER", order -> shortId(order.id()), 120);
        TableColumn<WorkOrderDto, String> machine = column("MACHINE", WorkOrderDto::machineId, 130);
        TableColumn<WorkOrderDto, String> title = column("TASK", WorkOrderDto::title, 260);
        TableColumn<WorkOrderDto, String> priority = column("PRIORITY", WorkOrderDto::priority, 100);
        priority.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(CRITICAL, !empty && ("HIGH".equalsIgnoreCase(item) || "CRITICAL".equalsIgnoreCase(item)));
                pseudoClassStateChanged(WARNING, !empty && "MEDIUM".equalsIgnoreCase(item));
                pseudoClassStateChanged(SUCCESS, !empty && "LOW".equalsIgnoreCase(item));
                setText(empty ? null : item);
            }
        });
        TableColumn<WorkOrderDto, String> status = column("LIFECYCLE", WorkOrderDto::status, 175);
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.replace('_', ' '));
                pseudoClassStateChanged(CRITICAL, "CANCELLED".equalsIgnoreCase(item));
                pseudoClassStateChanged(WARNING, "WAITING_FOR_PARTS".equalsIgnoreCase(item)
                        || "PENDING_RESERVATION".equalsIgnoreCase(item));
                pseudoClassStateChanged(SUCCESS, "COMPLETED".equalsIgnoreCase(item)
                        || "IN_PROGRESS".equalsIgnoreCase(item));
            }
        });
        TableColumn<WorkOrderDto, WorkOrderDto> transition = new TableColumn<>("TRANSITION");
        transition.setCellValueFactory(value -> new javafx.beans.property.SimpleObjectProperty<>(value.getValue()));
        transition.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<String> choices = new ComboBox<>();

            {
                choices.setPromptText("Move to…");
                choices.setOnAction(event -> {
                    String target = choices.getValue();
                    if (target != null) transitionTo(getItem(), target);
                });
            }

            @Override
            protected void updateItem(WorkOrderDto order, boolean empty) {
                super.updateItem(order, empty);
                if (empty || order == null) {
                    setGraphic(null);
                    return;
                }
                choices.setItems(javafx.collections.FXCollections.observableArrayList(
                        TRANSITIONS.getOrDefault(order.status(), List.of())));
                choices.setValue(null);
                choices.setDisable(choices.getItems().isEmpty());
                setGraphic(choices);
            }
        });
        table.getColumns().addAll(id, machine, title, priority, status, transition);
    }

    private TableColumn<WorkOrderDto, String> column(String heading,
                                                      java.util.function.Function<WorkOrderDto, String> value,
                                                      double width) {
        TableColumn<WorkOrderDto, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void transitionTo(WorkOrderDto order, String target) {
        if (order == null || !TRANSITIONS.getOrDefault(order.status(), List.of()).contains(target)) return;
        statusLabel.setText("Transitioning " + shortId(order.id()) + " → " + target.replace('_', ' ') + "…");
        if (demo) {
            TrafficEventBus.publish("REST", "REQUEST", "PENDING");
            replaceOrder(order, withStatus(order, target));
            TrafficEventBus.publish("REST", "RESPONSE", "SUCCESS");
            statusLabel.setText("DEMO MODE · lifecycle transition simulated");
            return;
        }
        rest.updateWorkOrderStatus(order.id(), target).whenComplete((updated, error) -> Platform.runLater(() -> {
            if (error != null) {
                statusLabel.setText("Transition failed · " + error.getMessage());
                table.refresh();
                return;
            }
            replaceOrder(order, updated);
            statusLabel.setText("Work order moved to " + target.replace('_', ' '));
        }));
    }

    private void replaceOrder(WorkOrderDto previous, WorkOrderDto updated) {
        int index = table.getItems().indexOf(previous);
        if (index >= 0) {
            table.getItems().set(index, updated);
            table.getSelectionModel().select(updated);
            Motion.enter(table).play();
        }
    }

    private void loadData() {
        if (demo) {
            table.getItems().setAll(List.of(
                    new WorkOrderDto("WO-1A42D7", "MACHINE-003", "ALT-7A31C9", "Inspect thermal bearing",
                            "Thermal envelope exceeded; confirm lubrication and alignment.", "OPEN", "HIGH",
                            "TECH-01", null, Instant.now().minusSeconds(5400)),
                    new WorkOrderDto("WO-2C80B1", "MACHINE-002", "ALT-2F10B4", "Inspect drive vibration",
                            "Review drive assembly and recheck vibration spectrum.", "CHECKING_INVENTORY", "MEDIUM",
                            "TECH-04", null, Instant.now().minusSeconds(8200)),
                    new WorkOrderDto("WO-53E91A", "MACHINE-001", null, "Replace pressure transducer",
                            "Sensor drift observed during pressure oscillation.", "READY_FOR_MAINTENANCE", "LOW",
                            "TECH-02", "RSV-8801", Instant.now().minusSeconds(14400)),
                    new WorkOrderDto("WO-18F3C4", "MACHINE-004", null, "Inspect spindle lubrication",
                            "Preventive maintenance on spindle assembly.", "IN_PROGRESS", "MEDIUM",
                            "TECH-03", null, Instant.now().minusSeconds(18600))));
            statusLabel.setText("DEMO MODE · lifecycle controls are simulated");
            return;
        }
        rest.getWorkOrders().whenComplete((orders, error) -> Platform.runLater(() -> {
            if (error != null) {
                statusLabel.setText("Could not load work orders · " + error.getMessage());
                return;
            }
            table.getItems().setAll(orders);
            statusLabel.setText("Showing " + orders.size() + " dispatched work orders");
        }));
    }

    private static WorkOrderDto withStatus(WorkOrderDto order, String status) {
        return new WorkOrderDto(order.id(), order.machineId(), order.alertId(), order.title(),
                order.description(), status, order.priority(), order.assignedTechnicianId(),
                order.reservationReference(), order.createdAt());
    }

    private static String shortId(String id) {
        return id != null && id.length() > 8 ? id.substring(0, 8) + "…" : id;
    }
}
