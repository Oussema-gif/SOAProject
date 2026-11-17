package com.soa.desktop.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class TopologyNode extends VBox {

    public TopologyNode(String index, String title, String protocol, String detail) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(7);
        setPadding(new Insets(15));
        getStyleClass().add("topology-node");
        Label indexLabel = new Label(index);
        indexLabel.getStyleClass().add("topology-index");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("topology-title");
        Label protocolLabel = new Label(protocol);
        protocolLabel.getStyleClass().addAll("topology-protocol", "protocol-" + protocol.toLowerCase());
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("topology-detail");
        getChildren().addAll(indexLabel, titleLabel, protocolLabel, detailLabel);
    }
}
