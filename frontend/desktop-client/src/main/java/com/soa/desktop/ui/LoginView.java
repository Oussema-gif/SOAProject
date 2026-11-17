package com.soa.desktop.ui;

import com.soa.desktop.ui.motion.Motion;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.BiConsumer;

public final class LoginView extends StackPane {

    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label message = new Label("Your credentials are never displayed or stored here.");
    private final Button submit = new Button("SIGN IN TO WORKSPACE");

    public LoginView(BiConsumer<String, String> onSubmit) {
        getStyleClass().add("login-screen");

        HBox layout = new HBox(76, buildHero(), buildLoginCard(onSubmit));
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(58, 72, 58, 72));
        layout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        layout.getStyleClass().add("login-layout");
        HBox.setHgrow(layout.getChildren().getFirst(), Priority.ALWAYS);

        widthProperty().addListener((observable, oldWidth, newWidth) -> {
            boolean compact = newWidth.doubleValue() < 1180;
            layout.setSpacing(compact ? 42 : 76);
            layout.setPadding(compact
                    ? new Insets(38, 34, 38, 34)
                    : new Insets(58, 72, 58, 72));
        });

        getChildren().add(layout);
        setAlignment(Pos.CENTER);
    }

    public void setBusy(boolean busy) {
        submit.setDisable(busy);
        submit.setText(busy ? "AUTHENTICATING..." : "SIGN IN TO WORKSPACE");
        message.setText(busy ? "Verifying your operator credentials..." : message.getText());
        message.getStyleClass().remove("error-text");
    }

    public void showError(String error) {
        submit.setDisable(false);
        submit.setText("RETRY SIGN IN");
        message.setText(error);
        if (!message.getStyleClass().contains("error-text")) message.getStyleClass().add("error-text");
        if (!Motion.isReducedMotion()) {
            Timeline shake = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(70), new KeyValue(translateXProperty(), -9, Motion.interpolator())),
                    new KeyFrame(Duration.millis(140), new KeyValue(translateXProperty(), 8, Motion.interpolator())),
                    new KeyFrame(Duration.millis(210), new KeyValue(translateXProperty(), -5, Motion.interpolator())),
                    new KeyFrame(Duration.millis(280), new KeyValue(translateXProperty(), 0, Motion.interpolator())));
            shake.play();
        }
    }

    private VBox buildHero() {
        VBox hero = new VBox(30);
        hero.setMinWidth(390);
        hero.setMaxWidth(780);
        hero.getStyleClass().add("login-hero");

        HBox brand = new HBox(13);
        brand.setAlignment(Pos.CENTER_LEFT);
        StackPane mark = new StackPane(new Label("K"));
        mark.getStyleClass().add("login-brand-mark");
        Label brandName = new Label("KINETIC");
        brandName.getStyleClass().add("login-brand-name");
        Label brandDetail = new Label("OPERATIONS INTELLIGENCE");
        brandDetail.getStyleClass().add("login-brand-detail");
        VBox brandCopy = new VBox(3, brandName, brandDetail);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label accessTag = new Label("CONTROL PLANE");
        accessTag.getStyleClass().add("login-access-tag");
        brand.getChildren().addAll(mark, brandCopy, spacer, accessTag);

        VBox headline = new VBox(0);
        Label kicker = new Label("MAINTENANCE  /  INVENTORY  /  TELEMETRY");
        kicker.getStyleClass().add("login-kicker");
        Label title = new Label("Clarity for every");
        title.getStyleClass().add("login-hero-title");
        Label accentTitle = new Label("moving part.");
        accentTitle.getStyleClass().addAll("login-hero-title", "login-hero-accent");
        Label description = new Label(
                "One secure workspace to monitor assets, resolve alerts, and keep operations moving.");
        description.getStyleClass().add("login-hero-description");
        description.setWrapText(true);
        description.setMaxWidth(520);
        VBox.setMargin(description, new Insets(17, 0, 0, 0));
        headline.getChildren().addAll(kicker, title, accentTitle, description);
        VBox.setMargin(kicker, new Insets(0, 0, 15, 0));

        VBox fabric = buildServiceFabric();
        HBox trust = new HBox(18);
        trust.setAlignment(Pos.CENTER_LEFT);
        Label roleAccess = new Label("ROLE-BASED ACCESS");
        roleAccess.getStyleClass().add("login-trust-label");
        Label auditReady = new Label("AUDIT-READY WORKFLOWS");
        auditReady.getStyleClass().add("login-trust-label");
        trust.getChildren().addAll(roleAccess, auditReady);

        hero.getChildren().addAll(brand, headline, fabric, trust);
        return hero;
    }

    private VBox buildServiceFabric() {
        VBox fabric = new VBox(17);
        fabric.getStyleClass().add("login-fabric");
        HBox heading = new HBox();
        heading.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("ONE CONTROL PLANE");
        title.getStyleClass().add("login-fabric-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label count = new Label("03 CONNECTED SERVICES");
        count.getStyleClass().add("login-fabric-count");
        heading.getChildren().addAll(title, spacer, count);

        HBox services = new HBox(10,
                serviceTile("REST", "OPERATIONS", "login-service-cyan"),
                serviceTile("SOAP", "INVENTORY", "login-service-violet"),
                serviceTile("gRPC", "LIVE SIGNAL", "login-service-green"));
        services.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node node : services.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        fabric.getChildren().addAll(heading, services);
        return fabric;
    }

    private VBox serviceTile(String protocol, String function, String toneClass) {
        Label protocolLabel = new Label(protocol);
        protocolLabel.getStyleClass().addAll("login-service-protocol", toneClass);
        Label functionLabel = new Label(function);
        functionLabel.getStyleClass().add("login-service-function");
        VBox tile = new VBox(8, protocolLabel, functionLabel);
        tile.getStyleClass().add("login-service-tile");
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    private VBox buildLoginCard(BiConsumer<String, String> onSubmit) {
        VBox card = new VBox(22);
        card.setMinWidth(380);
        card.setPrefWidth(420);
        card.setMaxWidth(460);
        card.setPadding(new Insets(36));
        card.getStyleClass().add("login-form-card");

        HBox topLine = new HBox();
        topLine.setAlignment(Pos.CENTER_LEFT);
        Label eyebrow = new Label("OPERATOR ACCESS");
        eyebrow.getStyleClass().add("login-form-eyebrow");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label privateTag = new Label("PRIVATE");
        privateTag.getStyleClass().add("login-private-tag");
        topLine.getChildren().addAll(eyebrow, spacer, privateTag);

        VBox heading = new VBox(8);
        Label title = new Label("Welcome back");
        title.getStyleClass().add("login-form-title");
        Label detail = new Label("Sign in with your assigned operator credentials.");
        detail.getStyleClass().add("login-form-description");
        detail.setWrapText(true);
        heading.getChildren().addAll(title, detail);

        VBox fields = new VBox(16);
        Label userLabel = new Label("OPERATOR ID");
        userLabel.getStyleClass().add("login-field-label");
        username.setPromptText("Enter your operator ID");
        username.setAccessibleText("Operator ID");
        username.getStyleClass().addAll("login-field", "login-input");
        Label passwordLabel = new Label("PASSWORD");
        passwordLabel.getStyleClass().add("login-field-label");
        password.setPromptText("Enter your password");
        password.setAccessibleText("Password");
        password.getStyleClass().addAll("login-field", "login-input");
        fields.getChildren().addAll(userLabel, username, passwordLabel, password);
        VBox.setMargin(passwordLabel, new Insets(2, 0, -8, 0));

        message.getStyleClass().add("login-message");
        message.setWrapText(true);
        message.setMinHeight(32);
        message.setMaxWidth(Double.MAX_VALUE);

        submit.getStyleClass().addAll("primary-button", "login-submit");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setPrefHeight(52);
        submit.setDefaultButton(true);
        submit.setOnAction(event -> submit(onSubmit));
        username.setOnAction(event -> password.requestFocus());
        password.setOnAction(event -> submit(onSubmit));

        HBox security = new HBox(8);
        security.setAlignment(Pos.CENTER_LEFT);
        Label securityMark = new Label("01");
        securityMark.getStyleClass().add("login-security-index");
        Label securityNote = new Label("SESSION ENCRYPTED  ·  AUDIT LOGGED");
        securityNote.getStyleClass().add("login-security-note");
        security.getChildren().addAll(securityMark, securityNote);

        card.getChildren().addAll(topLine, heading, fields, message, submit, security);
        return card;
    }

    private void submit(BiConsumer<String, String> onSubmit) {
        String user = username.getText().trim();
        String secret = password.getText();
        if (user.isEmpty() || secret.isEmpty()) {
            showError("Enter both your operator ID and password.");
            return;
        }
        setBusy(true);
        onSubmit.accept(user, secret);
    }
}
