package com.soa.desktop.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class TopologyNode extends VBox {

    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass OFFLINE = PseudoClass.getPseudoClass("offline");
    private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");

    private final Label title;
    private final StatusChip health = new StatusChip("NOT CHECKED", "offline");

    public TopologyNode(String index, String title, String protocol, String detail) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(7);
        setPadding(new Insets(15));
        getStyleClass().add("topology-node");
        setMinWidth(140);
        setPrefWidth(190);
        setMaxWidth(190);
        setMinHeight(140);
        setPrefHeight(140);
        setFocusTraversable(true);
        setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
        Label indexLabel = new Label(index);
        indexLabel.getStyleClass().add("topology-index");
        this.title = new Label(title);
        this.title.getStyleClass().add("topology-title");
        this.title.setWrapText(true);
        Label protocolLabel = new Label(protocol);
        protocolLabel.getStyleClass().addAll("topology-protocol", "protocol-" + protocol.toLowerCase());
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("topology-detail");
        detailLabel.setWrapText(true);
        health.getStyleClass().add("topology-health");
        health.setWrapText(true);
        getChildren().addAll(indexLabel, this.title, protocolLabel, detailLabel, health);
    }

    public void setHealth(String state, String detail) {
        health.setText(detail);
        health.setState(state);
        pseudoClassStateChanged(SUCCESS, "success".equalsIgnoreCase(state) || "normal".equalsIgnoreCase(state));
        pseudoClassStateChanged(WARNING, "warning".equalsIgnoreCase(state));
        pseudoClassStateChanged(CRITICAL, "critical".equalsIgnoreCase(state));
        pseudoClassStateChanged(OFFLINE, "offline".equalsIgnoreCase(state));
        pseudoClassStateChanged(LOADING, "loading".equalsIgnoreCase(state)
                || "connecting".equalsIgnoreCase(state) || "checking".equalsIgnoreCase(state));
    }

    public String getTitle() {
        return title.getText();
    }

    public String getHealthText() {
        return health.getText();
    }
}
