package com.soa.desktop.ui.topology;

import com.soa.desktop.ui.motion.Motion;
import com.soa.desktop.ui.motion.SpringInterpolator;
import javafx.animation.PathTransition;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PulseLayer extends Pane {

    private static final int MAX_ACTIVE = 40;
    private enum Protocol { REST, SOAP, GRPC }

    private final Map<Protocol, ArrayDeque<Node>> pool = new EnumMap<>(Protocol.class);
    private final List<PathTransition> active = new ArrayList<>();

    public PulseLayer() {
        setMouseTransparent(true);
        getStyleClass().add("pulse-layer");
        for (Protocol protocol : Protocol.values()) pool.put(protocol, new ArrayDeque<>());
    }

    public void pulse(Path path, String protocolName, String status) {
        if (active.size() >= MAX_ACTIVE || Motion.isReducedMotion()) return;
        Protocol protocol = parse(protocolName);
        Node pulse = acquire(protocol);
        pulse.getStyleClass().removeAll("pulse-rest", "pulse-soap", "pulse-grpc", "pulse-failed");
        pulse.getStyleClass().add("pulse-" + protocol.name().toLowerCase());
        if ("FAILED".equalsIgnoreCase(status)) pulse.getStyleClass().add("pulse-failed");
        getChildren().add(pulse);
        pulse.setCache(true);
        pulse.setCacheHint(javafx.scene.CacheHint.SPEED);

        PathTransition transition = new PathTransition(Duration.millis(680), path, pulse);
        transition.setOrientation(PathTransition.OrientationType.ORTHOGONAL_TO_TANGENT);
        transition.setInterpolator(new SpringInterpolator(10, 8));
        active.add(transition);
        transition.setOnFinished(event -> {
            active.remove(transition);
            getChildren().remove(pulse);
            pool.get(protocol).addLast(pulse);
        });
        transition.play();
    }

    private Node acquire(Protocol protocol) {
        Node recycled = pool.get(protocol).pollFirst();
        if (recycled != null) return recycled;
        if (protocol == Protocol.SOAP) {
            Rectangle capsule = new Rectangle(9, 5);
            capsule.setArcWidth(5);
            capsule.setArcHeight(5);
            return capsule;
        }
        if (protocol == Protocol.GRPC) return new Circle(5);
        return new Circle(4);
    }

    private static Protocol parse(String protocol) {
        try {
            return Protocol.valueOf(protocol.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Protocol.REST;
        }
    }

    public void stop() {
        for (PathTransition transition : List.copyOf(active)) transition.stop();
        active.clear();
        getChildren().clear();
        pool.values().forEach(ArrayDeque::clear);
    }
}
