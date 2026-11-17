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
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
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
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MainApp extends Application {

    private record Page(String id, String label) { }

    private static final List<Page> PAGES = List.of(
            new Page("dashboard", "Command deck"),
            new Page("machines", "Machines"),
            new Page("alerts", "Alerts"),
            new Page("maintenance", "Work orders"),
            new Page("inventory", "Inventory"),
            new Page("topology", "Live topology"),
            new Page("activity", "Protocol trace"));

    private MaintenanceRestClient rest;
    private volatile InventorySoapClient soap;
    private StackPane rootStack;
    private StackPane contentArea;
    private VBox commandPalette;
    private TextField paletteSearch;
    private Region paletteBackdrop;
    private final Map<String, StatusChip> protocolChips = new LinkedHashMap<>();
    private final List<Button> navButtons = new ArrayList<>();
    private Region activeIndicator;
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
        stage.setMinWidth(1280);
        stage.setMinHeight(800);
        stage.setWidth(1440);
        stage.setHeight(900);

        if (demo) {
            operator = "DEMO OPERATOR";
            buildWorkspaceShell();
            return;
        }

        CompletableFuture.supplyAsync(InventorySoapClient::new)
                .whenComplete((client, error) -> Platform.runLater(() -> soap = client));
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
        application.setLeft(buildSidebar());
        BorderPane workspace = new BorderPane();
        workspace.setTop(buildTopbar());
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        workspace.setCenter(contentArea);
        application.setCenter(workspace);
        rootStack.getChildren().add(application);
        buildCommandPalette();

        Scene scene = createScene(rootStack, 1440, 900);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
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
            for (String protocol : List.of("REST", "SOAP", "gRPC")) {
                protocolChips.get(protocol).setText(protocol + " · DEMO");
                protocolChips.get(protocol).setState("success");
            }
        }
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

    private VBox buildSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.getStyleClass().add("sidebar");
        VBox brand = new VBox(5);
        brand.getStyleClass().add("brand-lockup");
        Label title = new Label("KINETIC");
        title.getStyleClass().add("brand-name");
        Label subtitle = new Label("TELEMETRY SYSTEMS");
        subtitle.getStyleClass().add("brand-subtitle");
        brand.getChildren().addAll(title, subtitle);

        VBox navigation = new VBox(6);
        navigation.getStyleClass().add("sidebar-nav");
        for (int i = 0; i < PAGES.size(); i++) {
            Page page = PAGES.get(i);
            HBox contents = new HBox(12);
            contents.setAlignment(Pos.CENTER_LEFT);
            SVGPath icon = new SVGPath();
            icon.setContent(navigationIcon(i));
            icon.getStyleClass().add("nav-icon");
            StackPane iconFrame = new StackPane(icon);
            iconFrame.setMinSize(18, 18);
            iconFrame.setPrefSize(18, 18);
            iconFrame.setMaxSize(18, 18);
            Label index = new Label(String.format("%02d", i + 1));
            index.getStyleClass().add("nav-index");
            Label text = new Label(page.label());
            contents.getChildren().addAll(iconFrame, index, text);
            Button button = new Button();
            button.setGraphic(contents);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setPrefHeight(44);
            button.getStyleClass().add("nav-button");
            button.setOnAction(event -> switchWorkspace(page.id()));
            navigation.getChildren().add(button);
            navButtons.add(button);
        }
        activeIndicator = new Region();
        activeIndicator.getStyleClass().add("nav-active-indicator");
        activeIndicator.setPrefHeight(28);
        activeIndicator.setMaxHeight(28);
        StackPane navigationFrame = new StackPane(navigation, activeIndicator);
        StackPane.setAlignment(activeIndicator, Pos.TOP_LEFT);
        StackPane.setMargin(activeIndicator, new Insets(8, 0, 0, 0));
        VBox.setVgrow(navigationFrame, Priority.NEVER);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        ToggleButton reduceMotion = new ToggleButton("Reduce motion");
        reduceMotion.setMaxWidth(Double.MAX_VALUE);
        reduceMotion.setOnAction(event -> {
            Motion.setReducedMotion(reduceMotion.isSelected());
            rootStack.getStyleClass().remove("reduce-motion");
            if (reduceMotion.isSelected()) rootStack.getStyleClass().add("reduce-motion");
        });
        Label environment = new Label(demo ? "SIMULATION / ACTIVE" : "CONTROL LINK / READY");
        environment.getStyleClass().add("brand-subtitle");
        VBox footer = new VBox(9, reduceMotion, environment);
        footer.getStyleClass().add("sidebar-footer");
        sidebar.getChildren().addAll(brand, navigationFrame, spacer, footer);
        return sidebar;
    }

    private static String navigationIcon(int index) {
        return switch (index) {
            case 0 -> "M2 2H8V8H2ZM12 2H18V8H12ZM2 12H8V18H2ZM12 12H18V18H12Z";
            case 1 -> "M10 1A9 9 0 1 0 10 19A9 9 0 1 0 10 1M10 4V10L14 12";
            case 2 -> "M10 1L19 18H1ZM10 7V12M10 15V16";
            case 3 -> "M3 3H17V7H3ZM3 10H17V14H3ZM3 17H17V19H3Z";
            case 4 -> "M2 5L10 1L18 5V15L10 19L2 15ZM2 5L10 9L18 5M10 9V19";
            case 5 -> "M10 2L17 6V14L10 18L3 14V6ZM3 6L10 10L17 6M10 10V18";
            default -> "M1 10H5L8 4L12 16L15 10H19";
        };
    }

    private HBox buildTopbar() {
        HBox topbar = new HBox(12);
        topbar.getStyleClass().add("topbar");
        Label context = new Label("PIT WALL  /  PLANT 01");
        context.getStyleClass().add("topbar-context");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        for (String protocol : List.of("REST", "SOAP", "gRPC")) {
            StatusChip chip = new StatusChip(protocol + " · WAIT", "offline");
            protocolChips.put(protocol, chip);
            topbar.getChildren().add(chip);
        }
        Label user = new Label(operator + "  /  OPERATOR");
        user.getStyleClass().add("topbar-user");
        topbar.getChildren().addAll(0, List.of(context, spacer));
        topbar.getChildren().add(user);
        return topbar;
    }

    private void updateProtocolStatus(String protocol, String status) {
        if (demo) return;
        String key = protocol.equalsIgnoreCase("grpc") ? "gRPC" : protocol.toUpperCase();
        StatusChip chip = protocolChips.get(key);
        if (chip == null) return;
        if ("FAILED".equalsIgnoreCase(status)) {
            chip.setText(key + " · DOWN");
            chip.setState("offline");
        } else if ("SUCCESS".equalsIgnoreCase(status)) {
            chip.setText(key + " · LIVE");
            chip.setState("success");
        }
    }

    private void switchWorkspace(String pageId) {
        if (contentArea == null) return;
        activeDisposer.run();
        activeDisposer = () -> { };
        int selected = 0;
        for (int i = 0; i < PAGES.size(); i++) {
            boolean active = PAGES.get(i).id().equals(pageId);
            navButtons.get(i).getStyleClass().remove("active");
            if (active) {
                navButtons.get(i).getStyleClass().add("active");
                selected = i;
            }
        }
        if (activeIndicator != null) {
            TranslateTransition indicatorMotion = new TranslateTransition(
                    Duration.millis(Motion.isReducedMotion() ? 120 : 420), activeIndicator);
            indicatorMotion.setToY(selected * 50.0);
            indicatorMotion.setInterpolator(Motion.interpolator());
            indicatorMotion.play();
        }

        Node page;
        switch (pageId) {
            case "machines" -> {
                MonitoringView view = new MonitoringView("localhost", 9090, "MACHINE-001", rest, soap, demo);
                page = view;
                activeDisposer = view::dispose;
            }
            case "alerts" -> page = new AlertsView(rest, demo);
            case "maintenance" -> page = new MaintenanceView(rest, demo);
            case "inventory" -> page = new InventoryView(soap, demo);
            case "topology" -> {
                ArchitectureView view = new ArchitectureView(demo);
                page = view;
                activeDisposer = view::close;
            }
            case "activity" -> page = new ActivityView();
            default -> {
                DashboardView view = new DashboardView(rest, demo,
                        () -> switchWorkspace("alerts"), () -> switchWorkspace("machines"));
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
        paletteSearch.setPromptText("Search workspaces or commands…");
        VBox results = new VBox(5);
        for (int i = 0; i < PAGES.size(); i++) {
            Page page = PAGES.get(i);
            HBox item = new HBox(12);
            item.getStyleClass().add("palette-item");
            Label number = new Label(String.format("%02d", i + 1));
            number.getStyleClass().add("nav-index");
            Label title = new Label(page.label());
            title.getStyleClass().add("mono-text");
            item.getChildren().addAll(number, title);
            item.setOnMouseClicked(event -> {
                switchWorkspace(page.id());
                hideCommandPalette();
            });
            results.getChildren().add(item);
        }
        commandPalette.setPadding(new Insets(14));
        commandPalette.getChildren().addAll(paletteSearch, results);
        commandPalette.setVisible(false);
        StackPane.setAlignment(commandPalette, Pos.TOP_CENTER);
        StackPane.setMargin(commandPalette, new Insets(90, 0, 0, 0));
        paletteBackdrop = new Region();
        paletteBackdrop.getStyleClass().add("palette-backdrop");
        paletteBackdrop.setOnMouseClicked(event -> hideCommandPalette());
    }

    private void showCommandPalette() {
        if (commandPalette == null || commandPalette.isVisible()) return;
        rootStack.getChildren().addAll(paletteBackdrop, commandPalette);
        commandPalette.setVisible(true);
        paletteSearch.clear();
        paletteSearch.requestFocus();
        Motion.enter(commandPalette).play();
    }

    private void hideCommandPalette() {
        if (commandPalette == null) return;
        commandPalette.setVisible(false);
        rootStack.getChildren().removeAll(paletteBackdrop, commandPalette);
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
