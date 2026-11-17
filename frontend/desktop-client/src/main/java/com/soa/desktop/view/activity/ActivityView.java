package com.soa.desktop.view.activity;

import com.soa.desktop.util.ActivityLogger;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ActivityView extends VBox {

    public ActivityView() {
        setSpacing(24);

        VBox header = new VBox(8);
        Label sectionId = new Label("06 / ACTIVITY");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Protocol Activity Log");
        title.getStyleClass().add("editorial-title");
        Label desc = new Label("Real-time trace of REST, SOAP, and gRPC communication.");
        desc.getStyleClass().add("mono-muted");
        header.getChildren().addAll(sectionId, title, desc);

        TableView<ActivityLogger.LogEntry> table = new TableView<>(ActivityLogger.getLogs());
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ActivityLogger.LogEntry, String> colTime = new TableColumn<>("TIME");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().time()));
        colTime.setPrefWidth(120);

        TableColumn<ActivityLogger.LogEntry, String> colProto = new TableColumn<>("PROTOCOL");
        colProto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().protocol()));
        colProto.setPrefWidth(100);
        colProto.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    getStyleClass().removeAll("status-critical", "status-warning", "status-success", "mono-text");
                    if ("REST".equals(item)) getStyleClass().add("status-critical"); // Terracotta
                    else if ("SOAP".equals(item)) getStyleClass().add("mono-text"); // Graphite
                    else getStyleClass().add("status-success"); // gRPC
                }
            }
        });

        TableColumn<ActivityLogger.LogEntry, String> colOp = new TableColumn<>("OPERATION");
        colOp.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().operation()));
        colOp.setPrefWidth(250);

        TableColumn<ActivityLogger.LogEntry, String> colStatus = new TableColumn<>("STATUS");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));
        colStatus.setPrefWidth(120);

        TableColumn<ActivityLogger.LogEntry, String> colDetails = new TableColumn<>("DETAILS");
        colDetails.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().details()));

        table.getColumns().addAll(colTime, colProto, colOp, colStatus, colDetails);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, table);
    }
}