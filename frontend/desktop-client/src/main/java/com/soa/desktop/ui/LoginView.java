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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.BiConsumer;

public final class LoginView extends StackPane {

    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label message = new Label("Authenticate with the maintenance control plane.");
    private final Button submit = new Button("ESTABLISH SESSION");

    public LoginView(BiConsumer<String, String> onSubmit) {
        getStyleClass().add("login-screen");
        VBox frame = new VBox(26);
        frame.getStyleClass().add("login-frame");
        frame.setMaxWidth(470);
        frame.setPadding(new Insets(38));

        HBox eyebrow = new HBox(10);
        eyebrow.setAlignment(Pos.CENTER_LEFT);
        Label signal = new Label("●");
        signal.getStyleClass().add("signal-mark");
        Label brand = new Label("KINETIC // TELEMETRY");
        brand.getStyleClass().add("eyebrow");
        eyebrow.getChildren().addAll(signal, brand);

        VBox heading = new VBox(7);
        Label title = new Label("Pit wall access");
        title.getStyleClass().add("page-title");
        Label detail = new Label("Secure operator authentication · JWT");
        detail.getStyleClass().add("body-muted");
        heading.getChildren().addAll(title, detail);

        VBox fields = new VBox(14);
        Label userLabel = new Label("OPERATOR ID");
        userLabel.getStyleClass().add("field-label");
        username.setPromptText("admin");
        username.getStyleClass().add("login-field");
        Label passwordLabel = new Label("ACCESS KEY");
        passwordLabel.getStyleClass().add("field-label");
        password.setPromptText("Password");
        password.getStyleClass().add("login-field");
        fields.getChildren().addAll(userLabel, username, passwordLabel, password);

        message.getStyleClass().add("login-message");
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(event -> submit(onSubmit));
        username.setOnAction(event -> password.requestFocus());
        password.setOnAction(event -> submit(onSubmit));

        Label footer = new Label("REST / JWT     SOAP / API KEY     gRPC / STREAM");
        footer.getStyleClass().add("mono-muted");
        frame.getChildren().addAll(eyebrow, heading, fields, message, submit, footer);
        getChildren().add(frame);
        setAlignment(Pos.CENTER);
    }

    public void setBusy(boolean busy) {
        submit.setDisable(busy);
        submit.setText(busy ? "AUTHENTICATING…" : "ESTABLISH SESSION");
        message.setText(busy ? "Negotiating a secure session…" : message.getText());
        message.getStyleClass().remove("error-text");
    }

    public void showError(String error) {
        submit.setDisable(false);
        submit.setText("RETRY AUTHENTICATION");
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

    private void submit(BiConsumer<String, String> onSubmit) {
        String user = username.getText().trim();
        String secret = password.getText();
        if (user.isEmpty() || secret.isEmpty()) {
            showError("Enter both operator ID and access key.");
            return;
        }
        setBusy(true);
        onSubmit.accept(user, secret);
    }
}
