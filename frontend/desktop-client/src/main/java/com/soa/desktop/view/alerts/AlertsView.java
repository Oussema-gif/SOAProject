package com.soa.desktop.view.alerts;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.rest.dto.AlertDto;
import com.soa.desktop.client.rest.dto.WorkOrderDto;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
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
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class AlertsView extends VBox {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    private final MaintenanceRestClient rest;
    private final boolean demo;
    private final TableView<AlertDto> table = new TableView<>();
    private final Label statusLabel = new Label("Loading active incidents…");
    private final Label detailsLabel = new Label("Select an incident to inspect its history and linked work orders.");
    private final TextField search = new TextField();
    private final TextField machineFilter = new TextField();
    private final ComboBox<String> severityFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Button previousPage = new Button("← PREVIOUS");
    private final Button nextPage = new Button("NEXT →");
    private final Label pageLabel = new Label("");
    private final ObservableList<AlertDto> alerts = FXCollections.observableArrayList();
    private List<WorkOrderDto> workOrders = List.of();
    private final String initialSelection;
    private int pageIndex;
    private int totalElements;
    private static final int PAGE_SIZE = 20;

    public AlertsView(MaintenanceRestClient rest, boolean demo) {
        this(rest, demo, null);
    }

    public AlertsView(MaintenanceRestClient rest, boolean demo, String initialSelection) {
        this.rest = rest;
        this.demo = demo;
        this.initialSelection = initialSelection;
        getStyleClass().add("page");
        setSpacing(18);
        setPadding(new Insets(8));

        VBox header = new VBox(7);
        Label sectionId = new Label("03 / INCIDENT RESPONSE");
        sectionId.getStyleClass().add("section-label");
        Label title = new Label("Alert register");
        title.getStyleClass().add("page-title");
        statusLabel.getStyleClass().add("body-muted");
        header.getChildren().addAll(sectionId, title, statusLabel);

        HBox filters = buildFilters();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(view -> {
            TableRow<AlertDto> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && newValue != oldValue) Motion.enter(row).play();
            });
            return row;
        });
        createColumns();
        FilteredList<AlertDto> filtered = new FilteredList<>(alerts);
        table.setItems(filtered);
        search.textProperty().addListener((observable, oldValue, newValue) -> applyFilters(filtered));
        machineFilter.textProperty().addListener((observable, oldValue, newValue) -> applyFilters(filtered));
        severityFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters(filtered));
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters(filtered));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                showDetails(selected));
        detailsLabel.getStyleClass().add("mono-muted");
        detailsLabel.setWrapText(true);
        VBox details = new VBox(7);
        details.getStyleClass().add("surface-card");
        Label detailsHeading = new Label("INCIDENT INSPECTION");
        detailsHeading.getStyleClass().add("panel-heading");
        details.getChildren().addAll(detailsHeading, detailsLabel);
        previousPage.getStyleClass().add("small-button");
        nextPage.getStyleClass().add("small-button");
        previousPage.setOnAction(event -> {
            if (pageIndex > 0) {
                pageIndex--;
                loadPage();
            }
        });
        nextPage.setOnAction(event -> {
            if ((pageIndex + 1) * PAGE_SIZE < totalElements) {
                pageIndex++;
                loadPage();
            }
        });
        pageLabel.getStyleClass().add("mono-muted");
        HBox pager = new HBox(8, previousPage, pageLabel, nextPage);
        pager.setAlignment(Pos.CENTER_RIGHT);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(header, filters, table, pager, details);
        loadData();
    }

    private HBox buildFilters() {
        HBox filters = new HBox(8);
        filters.setAlignment(Pos.CENTER_LEFT);
        search.setPromptText("Search alert ID, metric or message");
        machineFilter.setPromptText("Machine ID");
        severityFilter.setItems(FXCollections.observableArrayList("ALL", "CRITICAL", "WARNING", "INFO"));
        statusFilter.setItems(FXCollections.observableArrayList("ALL", "ACTIVE", "ACKNOWLEDGED", "RESOLVED"));
        severityFilter.setValue("ALL");
        statusFilter.setValue("ALL");
        HBox.setHgrow(search, Priority.ALWAYS);
        filters.getChildren().addAll(search, machineFilter, severityFilter, statusFilter);
        return filters;
    }

    private void createColumns() {
        TableColumn<AlertDto, String> id = column("ALERT ID", alert -> shortId(alert.id()), 120);
        TableColumn<AlertDto, String> machine = column("MACHINE", AlertDto::machineId, 135);
        TableColumn<AlertDto, String> metric = column("METRIC", AlertDto::metricType, 120);
        TableColumn<AlertDto, String> severity = column("SEVERITY", AlertDto::severity, 100);
        severity.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(CRITICAL, !empty && "CRITICAL".equalsIgnoreCase(item));
                pseudoClassStateChanged(WARNING, !empty && "WARNING".equalsIgnoreCase(item));
                pseudoClassStateChanged(SUCCESS, !empty && "NORMAL".equalsIgnoreCase(item));
                setText(empty ? null : item);
            }
        });
        TableColumn<AlertDto, String> observed = column("OBSERVED", alert -> String.format("%.2f", alert.observedValue()), 100);
        TableColumn<AlertDto, String> threshold = column("THRESHOLD", alert -> String.format("%.2f", alert.thresholdValue()), 100);
        TableColumn<AlertDto, String> timestamp = column("TIMESTAMP",
                alert -> alert.occurredAt() == null ? "—" : TIME_FMT.format(alert.occurredAt()), 180);
        TableColumn<AlertDto, String> state = column("STATE", AlertDto::status, 115);

        TableColumn<AlertDto, AlertDto> action = new TableColumn<>("ACTION");
        action.setCellValueFactory(value -> new javafx.beans.property.SimpleObjectProperty<>(value.getValue()));
        action.setCellFactory(column -> new TableCell<>() {
            private final Button acknowledge = new Button("ACKNOWLEDGE");
            private final Button resolve = new Button("RESOLVE");
            private final HBox actions = new HBox(5, acknowledge, resolve);

            {
                acknowledge.getStyleClass().add("small-button");
                acknowledge.setOnAction(event -> acknowledgeAlert(getItem()));
                resolve.getStyleClass().addAll("small-button", "resolve-button");
                resolve.setOnAction(event -> resolveAlert(getItem()));
            }

            @Override
            protected void updateItem(AlertDto alert, boolean empty) {
                super.updateItem(alert, empty);
                boolean active = !empty && alert != null && "ACTIVE".equalsIgnoreCase(alert.status());
                acknowledge.setVisible(active);
                acknowledge.setManaged(active);
                resolve.setVisible(active || (!empty && alert != null
                        && "ACKNOWLEDGED".equalsIgnoreCase(alert.status())));
                resolve.setManaged(resolve.isVisible());
                setGraphic(empty || alert == null ? null : actions);
                setText(null);
            }
        });
        table.getColumns().addAll(id, machine, metric, severity, observed, threshold, timestamp, state, action);
    }

    private TableColumn<AlertDto, String> column(String title,
                                                  java.util.function.Function<AlertDto, String> value,
                                                  double width) {
        TableColumn<AlertDto, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void acknowledgeAlert(AlertDto alert) {
        if (alert == null) return;
        if (demo) {
            TrafficEventBus.publish("REST", "REQUEST", "PENDING");
            AlertDto acknowledged = new AlertDto(alert.id(), alert.machineId(), alert.metricType(), alert.severity(),
                    "ACKNOWLEDGED", alert.observedValue(), alert.thresholdValue(), alert.occurredAt(),
                    alert.createdAt(), alert.message());
            replaceAlert(acknowledged);
            TrafficEventBus.publish("REST", "RESPONSE", "SUCCESS");
            statusLabel.setText("DEMO MODE · acknowledgement simulated");
            return;
        }
        statusLabel.setText("Acknowledging alert " + shortId(alert.id()) + "…");
        rest.acknowledgeAlert(alert.id()).whenComplete((updated, error) -> Platform.runLater(() -> {
            if (error != null) {
                statusLabel.setText("Acknowledgement failed · " + error.getMessage());
                return;
            }
            replaceAlert(updated);
            statusLabel.setText("Alert acknowledged · " + shortId(updated.id()));
        }));
    }

    private void resolveAlert(AlertDto alert) {
        if (alert == null) return;
        if (demo) {
            AlertDto resolved = withStatus(alert, "RESOLVED");
            replaceAlert(resolved);
            statusLabel.setText("DEMO MODE · resolution simulated");
            return;
        }
        statusLabel.setText("Resolving alert " + shortId(alert.id()) + "…");
        rest.resolveAlert(alert.id()).whenComplete((updated, error) -> Platform.runLater(() -> {
            if (error != null) {
                statusLabel.setText("Resolution failed · " + error.getMessage());
                return;
            }
            replaceAlert(updated);
            statusLabel.setText("Alert resolved · " + shortId(updated.id()));
        }));
    }

    private void replaceAlert(AlertDto updated) {
        alerts.replaceAll(item -> item.id().equals(updated.id()) ? updated : item);
        applyFilters((FilteredList<AlertDto>) table.getItems());
        table.getSelectionModel().select(updated);
    }

    private static AlertDto withStatus(AlertDto alert, String status) {
        return new AlertDto(alert.id(), alert.machineId(), alert.metricType(), alert.severity(), status,
                alert.observedValue(), alert.thresholdValue(), alert.occurredAt(), alert.createdAt(), alert.message());
    }

    private void loadData() {
        if (demo) {
            alerts.setAll(List.of(
                    new AlertDto("ALT-7A31C9", "MACHINE-003", "TEMPERATURE", "CRITICAL", "ACTIVE",
                            91.8, 85, Instant.now().minusSeconds(95), Instant.now().minusSeconds(92), "Thermal envelope exceeded"),
                    new AlertDto("ALT-2F10B4", "MACHINE-002", "VIBRATION", "WARNING", "ACTIVE",
                            5.2, 4, Instant.now().minusSeconds(340), Instant.now().minusSeconds(330), "Vibration trending above limit"),
                    new AlertDto("ALT-8C04D2", "MACHINE-001", "PRESSURE", "WARNING", "ACKNOWLEDGED",
                            6.4, 6, Instant.now().minusSeconds(1200), Instant.now().minusSeconds(1180), "Pressure oscillation observed")));
            updateFilteredItems();
            pageLabel.setText("DEMO DATA · 3 incidents");
            statusLabel.setText("DEMO MODE · actions simulated");
            return;
        }
        pageIndex = 0;
        loadPage();
        rest.getWorkOrders().whenComplete((orders, error) -> {
            if (error == null) Platform.runLater(() -> {
                workOrders = orders;
                showDetails(table.getSelectionModel().getSelectedItem());
            });
        });
    }

    private void loadPage() {
        statusLabel.setText("Loading incident page " + (pageIndex + 1) + "…");
        rest.getAlertPage(pageIndex, PAGE_SIZE).whenComplete((response, error) -> Platform.runLater(() -> {
            if (error != null) {
                statusLabel.setText("Could not load active incidents · " + error.getMessage());
                return;
            }
            alerts.setAll(response.content());
            totalElements = response.totalElements();
            updateFilteredItems();
            int totalPages = Math.max(1, (int) Math.ceil(totalElements / (double) PAGE_SIZE));
            pageLabel.setText("PAGE " + (pageIndex + 1) + " / " + totalPages + " · " + totalElements + " incidents");
            statusLabel.setText("Incident register · " + totalElements + " total records");
        }));
        previousPage.setDisable(pageIndex == 0);
        nextPage.setDisable((pageIndex + 1) * PAGE_SIZE >= totalElements);
    }

    private void updateFilteredItems() {
        applyFilters((FilteredList<AlertDto>) table.getItems());
        previousPage.setDisable(pageIndex == 0);
        nextPage.setDisable((pageIndex + 1) * PAGE_SIZE >= totalElements);
        if (initialSelection != null) {
            alerts.stream().filter(alert -> alert.id() != null
                    && alert.id().toLowerCase(Locale.ROOT).contains(initialSelection.toLowerCase(Locale.ROOT)))
                    .findFirst().ifPresent(table.getSelectionModel()::select);
        } else if (!table.getItems().isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
    }

    private void applyFilters(FilteredList<AlertDto> filtered) {
        if (filtered == null) return;
        String text = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String machine = machineFilter.getText() == null
                ? "" : machineFilter.getText().trim().toLowerCase(Locale.ROOT);
        String severity = severityFilter.getValue();
        String status = statusFilter.getValue();
        filtered.setPredicate(alert -> (text.isEmpty() || matches(alert.id(), text)
                        || matches(alert.metricType(), text) || matches(alert.message(), text))
                && (machine.isEmpty() || matches(alert.machineId(), machine))
                && (severity == null || "ALL".equals(severity) || severity.equalsIgnoreCase(alert.severity())
                || "INFO".equals(severity) && "INFORMATIONAL".equalsIgnoreCase(alert.severity()))
                && (status == null || "ALL".equals(status) || status.equalsIgnoreCase(alert.status())));
    }

    private void showDetails(AlertDto alert) {
        if (alert == null) {
            detailsLabel.setText("Select an incident to inspect its history and linked work orders.");
            return;
        }
        String linked = workOrders.stream().filter(order -> alert.id().equals(order.alertId()))
                .map(order -> order.id() + " · " + order.status().replace('_', ' '))
                .reduce((first, second) -> first + "\n" + second).orElse("None in current work-order page");
        detailsLabel.setText("ID  " + alert.id() + "     MACHINE  " + alert.machineId()
                + "\n" + alert.metricType() + "  " + alert.observedValue() + " / " + alert.thresholdValue()
                + "     SEVERITY  " + alert.severity() + "     STATE  " + alert.status()
                + "\n" + (alert.message() == null ? "No incident message." : alert.message())
                + "\nOCCURRED  " + (alert.occurredAt() == null ? "—" : TIME_FMT.format(alert.occurredAt()))
                + "\nLINKED WORK ORDERS  " + linked);
    }

    private static boolean matches(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String shortId(String id) {
        return id != null && id.length() > 8 ? id.substring(0, 8) + "…" : id;
    }
}
