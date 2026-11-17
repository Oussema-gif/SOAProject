package com.soa.desktop.view.activity;

import com.soa.desktop.util.ActivityLogger;
import com.soa.desktop.ui.motion.Motion;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class ActivityView extends VBox {

    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    public ActivityView() {
        getStyleClass().add("page");
        setSpacing(18);
        setPadding(new Insets(8));

        VBox header = new VBox(7);
        Label sectionId = new Label("07 / SYSTEM TRACE");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Protocol activity");
        title.getStyleClass().add("page-title");
        Label description = new Label("Live REST, SOAP and gRPC traffic emitted by the desktop clients.");
        description.getStyleClass().add("body-muted");
        header.getChildren().addAll(sectionId, title, description);

        TableView<ActivityLogger.LogEntry> table = new TableView<>(ActivityLogger.getLogs());
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            TableRow<ActivityLogger.LogEntry> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && newValue != oldValue) Motion.enter(row).play();
            });
            return row;
        });

        TableColumn<ActivityLogger.LogEntry, String> time = column("TIME", ActivityLogger.LogEntry::time, 130);
        TableColumn<ActivityLogger.LogEntry, String> protocol = column("PROTOCOL", ActivityLogger.LogEntry::protocol, 100);
        protocol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                boolean failed = !empty && "FAILED".equalsIgnoreCase(getTableView().getItems()
                        .get(getIndex()).status());
                pseudoClassStateChanged(CRITICAL, failed);
                pseudoClassStateChanged(WARNING, !empty && "SOAP".equalsIgnoreCase(item));
                pseudoClassStateChanged(SUCCESS, !empty && "gRPC".equalsIgnoreCase(item));
                setText(empty ? null : item);
            }
        });
        TableColumn<ActivityLogger.LogEntry, String> operation =
                column("OPERATION", ActivityLogger.LogEntry::operation, 250);
        TableColumn<ActivityLogger.LogEntry, String> status =
                column("RESULT", ActivityLogger.LogEntry::status, 130);
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(CRITICAL, !empty && "FAILED".equalsIgnoreCase(item));
                pseudoClassStateChanged(SUCCESS, !empty && "SUCCESS".equalsIgnoreCase(item));
                setText(empty ? null : item);
            }
        });
        TableColumn<ActivityLogger.LogEntry, String> details =
                column("DETAILS", ActivityLogger.LogEntry::details, 400);
        table.getColumns().addAll(time, protocol, operation, status, details);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, table);
    }

    private TableColumn<ActivityLogger.LogEntry, String> column(String title,
                                                                java.util.function.Function<ActivityLogger.LogEntry, String> value,
                                                                double width) {
        TableColumn<ActivityLogger.LogEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
