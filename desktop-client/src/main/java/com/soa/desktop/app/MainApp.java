package com.soa.desktop.app;

import com.soa.desktop.client.rest.MaintenanceRestClient;
import com.soa.desktop.client.soap.InventorySoapClient;
import com.soa.desktop.util.ActivityLogger;
import com.soa.desktop.view.activity.ActivityView;
import com.soa.desktop.view.alerts.AlertsView;
import com.soa.desktop.view.dashboard.DashboardView;
import com.soa.desktop.view.inventory.InventoryView;
import com.soa.desktop.view.maintenance.MaintenanceView;
import com.soa.desktop.view.monitoring.MonitoringView;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.CompletableFuture;

public class MainApp extends Application {

    private MaintenanceRestClient rest;
    private InventorySoapClient soap;
    private StackPane contentArea;

    // Command Palette state
    private StackPane rootStack;
    private VBox commandPalette;
    private TextField paletteSearch;

    @Override
    public void start(Stage stage) {
        rest = new MaintenanceRestClient("http://localhost:8081/api/v1");
        soap = new InventorySoapClient();

        ActivityLogger.log("REST", "POST /auth/login", "PENDING", "Authenticating...");

        CompletableFuture.runAsync(() -> {
            try {
                String user = System.getenv().getOrDefault("ADMIN_USERNAME", "admin");
                String pass = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin");
                rest.login(user, pass);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenRun(() -> {
            Platform.runLater(() -> {
                ActivityLogger.log("REST", "POST /auth/login", "200 OK", "Logged in as admin");
                buildWorkspaceShell(stage);
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                ActivityLogger.log("REST", "POST /auth/login", "FAILED", ex.getMessage());
                System.err.println("Login failed: " + ex.getMessage());
            });
            return null;
        });
    }

    private void buildWorkspaceShell(Stage stage) {
        rootStack = new StackPane();
        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root");

        HBox identityStrip = buildIdentityStrip();
        mainLayout.setTop(identityStrip);

        contentArea = new StackPane();
        contentArea.setPadding(new Insets(32));
        mainLayout.setCenter(contentArea);

        rootStack.getChildren().add(mainLayout);
        buildCommandPalette();

        Scene scene = new Scene(rootStack, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/css/concrete-archive.css").toExternalForm());

        KeyCombination ctrlK = new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN);
        scene.getAccelerators().put(ctrlK, this::showCommandPalette);

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && commandPalette != null && commandPalette.isVisible()) {
                hideCommandPalette();
            }
        });

        stage.setTitle("SOA Telemetry — Concrete Archive");
        stage.setScene(scene);
        stage.show();

        switchWorkspace("01 / DASHBOARD");
    }

    private HBox buildIdentityStrip() {
        HBox strip = new HBox(24);
        strip.getStyleClass().add("identity-strip");
        strip.setAlignment(Pos.CENTER_LEFT);

        Label brand = new Label("SOA // TELEMETRY");
        brand.getStyleClass().add("brand-mark");

        HBox navContainer = new HBox(0);
        navContainer.setAlignment(Pos.CENTER_LEFT);

        String[] tabs = {"01 / DASHBOARD", "02 / MACHINES", "03 / ALERTS", "04 / MAINTENANCE", "05 / INVENTORY", "06 / ACTIVITY"};
        for (String tabText : tabs) {
            Label tab = new Label(tabText);
            tab.getStyleClass().add("nav-tab");
            tab.setOnMouseClicked(e -> switchWorkspace(tabText));
            navContainer.getChildren().add(tab);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox protocolStatus = new HBox(12);
        protocolStatus.setAlignment(Pos.CENTER_RIGHT);
        protocolStatus.getChildren().addAll(
                createProtocolIndicator("REST", "protocol-rest"),
                createProtocolIndicator("SOAP", "protocol-soap"),
                createProtocolIndicator("gRPC", "protocol-grpc")
        );

        Label shortcutHint = new Label("CTRL+K");
        shortcutHint.getStyleClass().add("mono-muted");
        shortcutHint.setStyle("-fx-border-color: #777B75; -fx-border-width: 1; -fx-padding: 2 6 2 6; -fx-border-radius: 3;");

        strip.getChildren().addAll(brand, navContainer, spacer, protocolStatus, shortcutHint);
        return strip;
    }

    private HBox createProtocolIndicator(String name, String cssClass) {
        HBox indicator = new HBox(6);
        indicator.setAlignment(Pos.CENTER);

        Region dot = new Region();
        dot.getStyleClass().addAll("protocol-dot", cssClass);

        Label label = new Label(name);
        label.getStyleClass().add("mono-muted");
        label.setStyle("-fx-font-size: 10px;");

        indicator.getChildren().addAll(dot, label);
        return indicator;
    }

    private void switchWorkspace(String tabText) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), contentArea);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);

        if (tabText.contains("DASHBOARD")) {
            contentArea.getChildren().setAll(new DashboardView(rest, () -> switchWorkspace("03 / ALERTS")));
        } else if (tabText.contains("MACHINES")) {
            contentArea.getChildren().setAll(new MonitoringView("localhost", 9090, "MACHINE-001", rest, soap));
        } else if (tabText.contains("ALERTS")) {
            contentArea.getChildren().setAll(new AlertsView(rest));
        } else if (tabText.contains("MAINTENANCE")) {
            contentArea.getChildren().setAll(new MaintenanceView(rest));
        } else if (tabText.contains("INVENTORY")) {
            contentArea.getChildren().setAll(new InventoryView(soap));
        } else if (tabText.contains("ACTIVITY")) {
            contentArea.getChildren().setAll(new ActivityView());
        }

        ft.playFromStart();
    }

    // --- Command Palette Logic ---

    private void buildCommandPalette() {
        commandPalette = new VBox();
        commandPalette.getStyleClass().add("palette-dialog");
        commandPalette.setMaxHeight(400);
        commandPalette.setMaxWidth(600);

        paletteSearch = new TextField();
        paletteSearch.setPromptText("Search workspaces, machines, or commands...");
        paletteSearch.getStyleClass().add("palette-search");

        VBox results = new VBox(8);
        results.setPadding(new Insets(16));
        results.getChildren().addAll(
                createPaletteItem("01 / DASHBOARD", "Go to Telemetry Dashboard"),
                createPaletteItem("02 / MACHINES", "Go to Machine List"),
                createPaletteItem("03 / ALERTS", "Go to Alert Management"),
                createPaletteItem("04 / MAINTENANCE", "Go to Work Orders"),
                createPaletteItem("05 / INVENTORY", "Go to Inventory"),
                createPaletteItem("06 / ACTIVITY", "Go to Protocol Activity")
        );

        commandPalette.getChildren().addAll(paletteSearch, results);
        StackPane.setAlignment(commandPalette, Pos.TOP_CENTER);
        StackPane.setMargin(commandPalette, new Insets(100, 0, 0, 0));
        commandPalette.setVisible(false);
    }

    private HBox createPaletteItem(String title, String subtitle) {
        HBox item = new HBox(12);
        item.setPadding(new Insets(8));
        item.setStyle("-fx-cursor: hand;");
        item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #F5F3ED; -fx-cursor: hand;"));
        item.setOnMouseExited(e -> item.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("mono-text");
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("mono-muted");

        item.getChildren().addAll(titleLabel, subLabel);
        item.setOnMouseClicked(e -> {
            switchWorkspace(title);
            hideCommandPalette();
        });
        return item;
    }

    private void showCommandPalette() {
        Region backdrop = new Region();
        backdrop.getStyleClass().add("palette-overlay");
        backdrop.setOnMouseClicked(e -> hideCommandPalette());

        rootStack.getChildren().addAll(backdrop, commandPalette);
        commandPalette.setVisible(true);
        paletteSearch.requestFocus();
    }

    private void hideCommandPalette() {
        if (commandPalette != null) {
            commandPalette.setVisible(false);
            rootStack.getChildren().remove(commandPalette);
        }
        if (rootStack.getChildren().size() > 1) {
            rootStack.getChildren().remove(1);
        }
    }

    @Override
    public void stop() {
        if (rest != null) rest.close();
        if (soap != null) soap.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}