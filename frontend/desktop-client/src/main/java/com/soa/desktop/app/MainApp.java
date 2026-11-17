package com.soa.desktop.app;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.ui.ArchitectureView;
import com.soa.desktop.ui.LoginView;
import com.soa.desktop.ui.components.StatusChip;
import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.topology.TrafficEventBus;
import com.soa.desktop.util.ActivityLogger;
import com.soa.desktop.view.activity.ActivityView;
import com.soa.desktop.view.alerts.AlertsView;
import com.soa.desktop.view.dashboard.DashboardView;
import com.soa.desktop.view.inventory.InventoryView;
import com.soa.desktop.view.maintenance.MaintenanceView;
import com.soa.desktop.view.monitoring.MonitoringView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MainApp extends Application {

    private record Page(String id, String label) { }
    private record CommandItem(String label, String detail, Runnable action) { }

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final List<Page> PAGES = List.of(
            new Page("dashboard", "Command deck"),
            new Page("machines", "Machine fleet"),
            new Page("alerts", "Alert register"),
            new Page("maintenance", "Work orders"),
            new Page("inventory", "Spare parts inventory"),
            new Page("topology", "Live topology"),
            new Page("activity", "Protocol activity"));

    private MaintenanceRestClient rest;
    private volatile InventorySoapClient soap;
    private StackPane rootStack;
    private StackPane contentArea;
    private VBox commandPalette;
    private TextField paletteSearch;
    private Region paletteBackdrop;
    private final Map<String, StatusChip> protocolChips = new LinkedHashMap<>();
    private final List<Button> navButtons = new ArrayList<>();
    private final ListView<CommandItem> paletteResults = new ListView<>();
    private final javafx.collections.ObservableList<CommandItem> commandCatalog = FXCollections.observableArrayList();
    private FilteredList<CommandItem> filteredCommands;
    private Button paletteTrigger;
    private Label clockLabel;
    private Label alertCount;
    private Timeline clock;
    private Node paletteReturnFocus;
    private AutoCloseable trafficSubscription;
    private Runnable activeDisposer = () -> { };
    private boolean demo;
    private String operator = "OPERATOR";
    private Stage stage;

    @Override
    public void start(Stage stage) {
        loadFonts();
        this.stage = stage;
        demo = demoEnabled(getParameters().getRaw(), System.getProperty("kt.demo"), System.getenv("KT_DEMO"));
        rest = new MaintenanceRestClient("http://localhost:8081/api/v1");
        stage.setTitle("Kinetic Telemetry · Pit Wall");
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.setWidth(1440);
        stage.setHeight(900);

        if (demo) {
            operator = "DEMO OPERATOR";
            buildWorkspaceShell();
            return;
        }

        CompletableFuture.supplyAsync(() -> new InventorySoapClient(
                        System.getenv("INVENTORY_API_KEY"),
                        System.getenv().getOrDefault("INVENTORY_WSDL_URL",
                                "http://localhost:8082/services/inventory.wsdl")))
                .whenComplete((client, error) -> Platform.runLater(() -> {
                    if (error == null) {
                        soap = client;
                        checkSoapHealth();
                    } else {
                        updateProtocolStatus("SOAP", "FAILED");
                    }
                }));
        showLogin();
    }

    private void loadFonts() {
        loadFont("/fonts/Syne-VF.ttf");
        loadFont("/fonts/JetBrainsMono-VF.ttf");
    }

    private void loadFont(String resource) {
        try (InputStream stream = MainApp.class.getResourceAsStream(resource)) {
            if (stream == null || Font.loadFont(stream, 12) == null) {
                throw new IllegalStateException("Could not load bundled font: " + resource);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load bundled font: " + resource, exception);
        }
    }

    private void showLogin() {
        LoginView login = new LoginView(this::authenticate);
        Scene scene = createScene(login, 1440, 900);
        stage.setScene(scene);
        stage.show();
        Animation entrance = Motion.enter(login);
        entrance.play();
    }

    private void authenticate(String username, String password) {
        CompletableFuture.supplyAsync(() -> rest.login(username, password))
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        LoginView view = (LoginView) stage.getScene().getRoot();
                        view.showError(rootMessage(error));
                        return;
                    }
                    operator = username.toUpperCase();
                    ActivityLogger.log("REST", "POST /auth/login", "SUCCESS", "Authenticated operator");
                    buildWorkspaceShell();
                }));
    }

    private void buildWorkspaceShell() {
        rootStack = new StackPane();
        BorderPane application = new BorderPane();
        application.getStyleClass().add("identity-shell");
        BorderPane workspace = new BorderPane();
        workspace.setTop(buildTopbar());
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        ScrollPane contentScroll = new ScrollPane(contentArea);
        contentScroll.setFitToWidth(true);
        contentScroll.setFitToHeight(false);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.getStyleClass().add("workspace-scroll");
        workspace.setCenter(contentScroll);
        application.setCenter(workspace);
        rootStack.getChildren().add(application);
        buildCommandPalette();

        Scene scene = createScene(rootStack, 1440, 900);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
                this::showCommandPalette);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.META_DOWN),
                this::showCommandPalette);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && commandPalette != null && commandPalette.isVisible()) {
                hideCommandPalette();
            }
        });
        stage.setScene(scene);
        stage.show();
        if (trafficSubscription == null) {
            trafficSubscription = TrafficEventBus.subscribe(event -> Platform.runLater(() -> {
                updateProtocolStatus(event.protocol(), event.status());
                if (demo) {
                    ActivityLogger.log(event.protocol(), event.direction(), event.status(), "Simulated traffic");
                }
            }));
        }
        if (demo) {
            alertCount.setText("ALERTS · SIMULATED");
            for (String protocol : List.of("REST", "SOAP", "gRPC")) {
                protocolChips.get(protocol).setText(protocol + " · SIMULATED");
                protocolChips.get(protocol).setState("offline");
            }
        } else {
            checkRestHealth();
            checkSoapHealth();
        }
        startClock();
        switchWorkspace("dashboard");
    }

    static boolean demoEnabled(List<String> arguments, String property, String environment) {
        return arguments.contains("--demo")
                || "true".equalsIgnoreCase(property)
                || "true".equalsIgnoreCase(environment);
    }

    private Scene createScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(MainApp.class.getResource("/css/kinetic.css").toExternalForm());
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            ButtonBase button = buttonFor(event.getTarget());
            if (button != null && !button.isDisabled()) Motion.scale(button, 0.96);
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            ButtonBase button = buttonFor(event.getTarget());
            if (button != null) Motion.scale(button, 1);
        });
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED, event -> {
            ButtonBase button = buttonFor(event.getTarget());
            if (button != null && !button.isDisabled()) Motion.scale(button, 1.015);
        });
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            ButtonBase button = buttonFor(event.getTarget());
            if (button != null) Motion.scale(button, 1);
        });
        return scene;
    }

    private static ButtonBase buttonFor(Object target) {
        if (!(target instanceof Node node)) return null;
        Node current = node;
        while (current != null && !(current instanceof ButtonBase)) current = current.getParent();
        return current instanceof ButtonBase button ? button : null;
    }

    private HBox buildWorkspaceNavigation() {
        HBox navigation = new HBox(5);
        navigation.getStyleClass().add("workspace-rail");
        for (int i = 0; i < PAGES.size(); i++) {
            Page page = PAGES.get(i);
            HBox contents = new HBox(12);
            contents.setAlignment(Pos.CENTER_LEFT);
            Label index = new Label(String.format("%02d", i + 1));
            index.getStyleClass().add("nav-index");
            Label text = new Label(page.label());
            text.getStyleClass().add("workspace-label");
            contents.getChildren().addAll(index, text);
            Button button = new Button();
            button.setGraphic(contents);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setPrefHeight(42);
            button.getStyleClass().add("nav-button");
            button.setTooltip(new Tooltip(page.label()));
            button.setOnAction(event -> switchWorkspace(page.id()));
            navigation.getChildren().add(button);
            navButtons.add(button);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        return navigation;
    }

    private VBox buildTopbar() {
        HBox topbar = new HBox(12);
        topbar.getStyleClass().add("topbar");
        VBox brand = new VBox(2);
        Label title = new Label("KINETIC");
        title.getStyleClass().add("brand-name");
        Label subtitle = new Label("TELEMETRY / PLANT 01");
        subtitle.getStyleClass().add("brand-subtitle");
        brand.getChildren().addAll(title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        for (String protocol : List.of("REST", "SOAP", "gRPC")) {
            StatusChip chip = new StatusChip(protocol + " · CHECKING", "offline");
            protocolChips.put(protocol, chip);
        }
        alertCount = new Label("ALERTS · —");
        alertCount.getStyleClass().add("topbar-context");
        clockLabel = new Label(CLOCK_FORMAT.format(LocalTime.now()));
        clockLabel.getStyleClass().add("topbar-clock");
        Label user = new Label(operator + "  /  OPERATOR");
        user.getStyleClass().add("topbar-user");
        ToggleButton reduceMotion = new ToggleButton("MOTION");
        reduceMotion.getStyleClass().add("motion-toggle");
        reduceMotion.setTooltip(new Tooltip("Toggle reduced motion"));
        reduceMotion.setOnAction(event -> {
            Motion.setReducedMotion(reduceMotion.isSelected());
            rootStack.getStyleClass().remove("reduce-motion");
            if (reduceMotion.isSelected()) rootStack.getStyleClass().add("reduce-motion");
        });
        paletteTrigger = new Button("⌘K / CTRL+K");
        paletteTrigger.getStyleClass().add("palette-trigger");
        paletteTrigger.setOnAction(event -> showCommandPalette());
        topbar.getChildren().addAll(brand, spacer, alertCount);
        topbar.getChildren().addAll(protocolChips.values());
        topbar.getChildren().addAll(clockLabel, user, reduceMotion, paletteTrigger);
        VBox shellTop = new VBox(0, topbar, buildWorkspaceNavigation());
        shellTop.getStyleClass().add("shell-top");
        return shellTop;
    }

    private void updateProtocolStatus(String protocol, String status) {
        if (demo) return;
        String key = protocol.equalsIgnoreCase("grpc") ? "gRPC" : protocol.toUpperCase();
        StatusChip chip = protocolChips.get(key);
        if (chip == null) return;
        if ("FAILED".equalsIgnoreCase(status)) setChip(chip, key + " · DOWN", "offline");
        else if ("SUCCESS".equalsIgnoreCase(status)) setChip(chip, key + " · LIVE", "success");
        else if ("PENDING".equalsIgnoreCase(status) && "gRPC".equals(key))
            setChip(chip, key + " · CONNECTING", "warning");
    }

    private void switchWorkspace(String pageId) {
        switchWorkspace(pageId, null);
    }

    private void switchWorkspace(String pageId, String selection) {
        if (contentArea == null) return;
        activeDisposer.run();
        activeDisposer = () -> { };
        for (int i = 0; i < PAGES.size(); i++) {
            boolean active = PAGES.get(i).id().equals(pageId);
            navButtons.get(i).getStyleClass().remove("active");
            if (active) {
                navButtons.get(i).getStyleClass().add("active");
            }
        }

        Node page;
        switch (pageId) {
            case "machines" -> {
                MonitoringView view = new MonitoringView("localhost", 9090,
                        selection == null ? "MACHINE-001" : selection, rest, soap, demo);
                page = view;
                activeDisposer = view::dispose;
            }
            case "alerts" -> page = new AlertsView(rest, demo, selection);
            case "maintenance" -> page = new MaintenanceView(rest, demo, selection);
            case "inventory" -> page = new InventoryView(soap, demo, selection);
            case "topology" -> {
                ArchitectureView view = new ArchitectureView(rest, soap, demo);
                page = view;
                activeDisposer = view::close;
            }
            case "activity" -> page = new ActivityView();
            default -> {
                DashboardView view = new DashboardView(rest, demo,
                        () -> switchWorkspace("alerts"), () -> switchWorkspace("machines"),
                        () -> switchWorkspace("maintenance"));
                page = view;
                activeDisposer = view::close;
            }
        }
        contentArea.getChildren().setAll(page);
        Animation entrance = Motion.enter(page);
        entrance.play();
    }

    private void buildCommandPalette() {
        commandPalette = new VBox(8);
        commandPalette.getStyleClass().add("command-palette");
        commandPalette.setMaxHeight(440);
        commandPalette.setMaxWidth(620);
        paletteSearch = new TextField();
        paletteSearch.setPromptText("Find a workspace, machine, alert or work order…");
        paletteResults.setMaxHeight(340);
        paletteResults.getStyleClass().add("palette-results");
        filteredCommands = new FilteredList<>(commandCatalog);
        paletteResults.setItems(filteredCommands);
        paletteResults.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(CommandItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox details = new VBox(3);
                Label title = new Label(item.label());
                title.getStyleClass().add("mono-text");
                Label detail = new Label(item.detail());
                detail.getStyleClass().add("mono-muted");
                details.getChildren().addAll(title, detail);
                setGraphic(details);
                setText(null);
            }
        });
        paletteResults.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) executeSelectedCommand();
        });
        paletteSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filteredCommands.setPredicate(item -> query.isEmpty()
                    || item.label().toLowerCase(Locale.ROOT).contains(query)
                    || item.detail().toLowerCase(Locale.ROOT).contains(query));
            if (!filteredCommands.isEmpty()) paletteResults.getSelectionModel().selectFirst();
        });
        paletteSearch.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN && !filteredCommands.isEmpty()) {
                int next = Math.min(paletteResults.getSelectionModel().getSelectedIndex() + 1,
                        filteredCommands.size() - 1);
                paletteResults.getSelectionModel().select(next);
                paletteResults.scrollTo(next);
                event.consume();
            } else if (event.getCode() == KeyCode.UP && !filteredCommands.isEmpty()) {
                int next = Math.max(0, paletteResults.getSelectionModel().getSelectedIndex() - 1);
                paletteResults.getSelectionModel().select(next);
                paletteResults.scrollTo(next);
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                executeSelectedCommand();
                event.consume();
            }
        });
        for (int i = 0; i < PAGES.size(); i++) {
            Page page = PAGES.get(i);
            addCommand(new CommandItem(page.label(), "Workspace",
                    () -> switchWorkspace(page.id())));
        }
        addCommand(new CommandItem("Search spare parts", "Inventory · look up a part ID",
                () -> switchWorkspace("inventory")));
        addCommand(new CommandItem("Inspect service health", "Live topology · REST, SOAP, gRPC and persistence",
                () -> switchWorkspace("topology")));
        commandPalette.setPadding(new Insets(14));
        commandPalette.getChildren().addAll(paletteSearch, paletteResults);
        commandPalette.setVisible(false);
        StackPane.setAlignment(commandPalette, Pos.TOP_CENTER);
        StackPane.setMargin(commandPalette, new Insets(90, 12, 0, 12));
        paletteBackdrop = new Region();
        paletteBackdrop.getStyleClass().add("palette-backdrop");
        paletteBackdrop.setOnMouseClicked(event -> hideCommandPalette());
    }

    private void showCommandPalette() {
        if (commandPalette == null || commandPalette.isVisible()) return;
        rootStack.getChildren().addAll(paletteBackdrop, commandPalette);
        commandPalette.setVisible(true);
        paletteReturnFocus = rootStack.getScene().getFocusOwner();
        paletteSearch.clear();
        paletteResults.getSelectionModel().selectFirst();
        paletteSearch.requestFocus();
        refreshCommandTargets();
        Platform.runLater(paletteSearch::requestFocus);
        Motion.enter(commandPalette).play();
    }

    private void hideCommandPalette() {
        if (commandPalette == null) return;
        commandPalette.setVisible(false);
        rootStack.getChildren().removeAll(paletteBackdrop, commandPalette);
        if (paletteReturnFocus != null && paletteReturnFocus.getScene() != null) paletteReturnFocus.requestFocus();
        else if (paletteTrigger != null) paletteTrigger.requestFocus();
    }

    private void refreshCommandTargets() {
        if (demo || !rest.hasToken()) return;
        rest.getMachines().thenAccept(machines -> Platform.runLater(() -> machines.forEach(machine -> {
            String name = machine.name() == null ? machine.id() : machine.name();
            addCommand(new CommandItem("Machine · " + name,
                    machine.id() + " · " + nullToDash(machine.status()),
                    () -> switchWorkspace("machines", machine.id())));
        })));
        rest.getActiveAlerts().thenAccept(alerts -> Platform.runLater(() -> alerts.forEach(alert ->
                addCommand(new CommandItem("Alert · " + shortId(alert.id()),
                        alert.machineId() + " · " + alert.severity() + " · " + alert.metricType(),
                        () -> switchWorkspace("alerts", alert.id()))))));
        rest.getWorkOrders().thenAccept(orders -> Platform.runLater(() -> orders.forEach(order ->
                addCommand(new CommandItem("Work order · " + shortId(order.id()),
                        order.machineId() + " · " + order.status() + " · " + order.title(),
                        () -> switchWorkspace("maintenance", order.id()))))));
    }

    private void addCommand(CommandItem item) {
        boolean exists = commandCatalog.stream()
                .anyMatch(existing -> existing.label().equals(item.label()));
        if (!exists) commandCatalog.add(item);
    }

    private void executeSelectedCommand() {
        CommandItem selected = paletteResults.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        selected.action().run();
        hideCommandPalette();
    }

    private void checkRestHealth() {
        rest.checkHealth().whenComplete((healthy, error) -> Platform.runLater(() ->
                setChip(protocolChips.get("REST"), error == null && healthy
                        ? "REST · LIVE" : "REST · DOWN", error == null && healthy ? "success" : "offline")));
        rest.getAlertPage(0, 1, "ACTIVE").whenComplete((page, error) -> Platform.runLater(() -> {
            if (error == null) alertCount.setText("ALERTS · " + page.totalElements());
            else alertCount.setText("ALERTS · UNAVAILABLE");
        }));
    }

    private void checkSoapHealth() {
        InventorySoapClient client = soap;
        if (client == null) return;
        client.checkAuthenticatedService().whenComplete((part, error) -> Platform.runLater(() ->
                setChip(protocolChips.get("SOAP"), error == null
                        ? "SOAP · LIVE" : "SOAP · DOWN", error == null ? "success" : "offline")));
    }

    private void startClock() {
        if (clock != null) clock.stop();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), event ->
                clockLabel.setText(CLOCK_FORMAT.format(LocalTime.now()))));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    private static void setChip(StatusChip chip, String text, String state) {
        if (chip == null) return;
        chip.setText(text);
        chip.setState(state);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String shortId(String id) {
        return id != null && id.length() > 8 ? id.substring(0, 8) + "…" : id;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override
    public void stop() {
        activeDisposer.run();
        if (rest != null) rest.close();
        if (soap != null) soap.close();
        if (clock != null) clock.stop();
        if (trafficSubscription != null) {
            try {
                trafficSubscription.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Could not detach protocol status listener", exception);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
