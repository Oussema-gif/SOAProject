package com.soa.desktop.view.activity;

import com.soa.desktop.util.ActivityLogger;
import com.soa.desktop.ui.motion.Motion;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.Locale;

public final class ActivityView extends VBox {

    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    private final TableView<ActivityLogger.LogEntry> table = new TableView<>();
    private final TextField search = new TextField();
    private final ComboBox<String> protocolFilter = new ComboBox<>();
    private final ToggleButton pause = new ToggleButton("PAUSE STREAM");
    private final Label details = new Label("Select a trace row to inspect the available request details.");
    private final FilteredList<ActivityLogger.LogEntry> filtered =
            new FilteredList<>(ActivityLogger.getLogs());
    private final ObservableList<ActivityLogger.LogEntry> pausedSnapshot = FXCollections.observableArrayList();

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

        search.setPromptText("Search operation, request ID, result or details");
        protocolFilter.setItems(FXCollections.observableArrayList("ALL", "REST", "SOAP", "gRPC"));
        protocolFilter.setValue("ALL");
        pause.setOnAction(event -> {
            if (pause.isSelected()) {
                pausedSnapshot.setAll(filtered);
                table.setItems(pausedSnapshot);
                pause.setText("RESUME STREAM");
            } else {
                table.setItems(filtered);
                pause.setText("PAUSE STREAM");
            }
        });
        Button reset = new Button("RESET FILTERS");
        reset.getStyleClass().add("small-button");
        reset.setOnAction(event -> {
            search.clear();
            protocolFilter.setValue("ALL");
        });
        HBox filters = new HBox(9, search, protocolFilter, pause, reset);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        table.setItems(filtered);
        search.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        protocolFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                showDetails(selected));
        details.getStyleClass().add("mono-muted");
        details.setWrapText(true);
        VBox detailPanel = new VBox(7);
        detailPanel.getStyleClass().add("surface-card");
        Label detailHeading = new Label("TRACE INSPECTION");
        detailHeading.getStyleClass().add("panel-heading");
        detailPanel.getChildren().addAll(detailHeading, details);

        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            TableRow<ActivityLogger.LogEntry> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && newValue != oldValue) Motion.enter(row).play();
            });
            return row;
        });

        TableColumn<ActivityLogger.LogEntry, String> time =
                column("TIMESTAMP", ActivityLogger.LogEntry::time, 190);
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
        TableColumn<ActivityLogger.LogEntry, String> requestId =
                column("REQUEST ID", entry -> valueOrDash(entry.requestId()), 180);
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
                column("DETAILS", ActivityLogger.LogEntry::details, 250);
        TableColumn<ActivityLogger.LogEntry, String> code =
                column("TRANSPORT STATUS", entry -> entry.httpStatus() != null
                        ? "HTTP " + entry.httpStatus() : valueOrDash(entry.protocolStatus()), 125);
        TableColumn<ActivityLogger.LogEntry, String> duration =
                column("DURATION", entry -> entry.durationMillis() == null
                        ? "—" : entry.durationMillis() + " ms", 90);
        table.getColumns().addAll(time, protocol, operation, requestId, status, code, duration, details);
        table.setPlaceholder(new Label("No protocol events captured yet."));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, filters, table, detailPanel);
    }

    private TableColumn<ActivityLogger.LogEntry, String> column(String title,
                                                                java.util.function.Function<ActivityLogger.LogEntry, String> value,
                                                                double width) {
        TableColumn<ActivityLogger.LogEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void applyFilters() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String protocol = protocolFilter.getValue();
        filtered.setPredicate(entry -> (protocol == null || "ALL".equals(protocol)
                        || protocol.equalsIgnoreCase(entry.protocol()))
                && (query.isEmpty() || contains(entry.time(), query) || contains(entry.protocol(), query)
                || contains(entry.operation(), query) || contains(entry.requestId(), query)
                || contains(entry.status(), query) || contains(entry.details(), query)));
        if (pause.isSelected()) pausedSnapshot.setAll(filtered);
    }

    private void showDetails(ActivityLogger.LogEntry entry) {
        if (entry == null) {
            details.setText("Select a trace row to inspect the available request details.");
            return;
        }
        details.setText("TIME  " + entry.time() + "     PROTOCOL  " + entry.protocol()
                + "     REQUEST  " + valueOrDash(entry.requestId())
                + "\nOPERATION  " + entry.operation() + "     RESULT  " + entry.status()
                + "     TRANSPORT STATUS  " + (entry.httpStatus() != null ? "HTTP " + entry.httpStatus()
                : valueOrDash(entry.protocolStatus()))
                + "     DURATION  " + (entry.durationMillis() == null
                ? "not measured" : entry.durationMillis() + " ms")
                + "\nDETAILS  " + valueOrDash(entry.details()));
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
