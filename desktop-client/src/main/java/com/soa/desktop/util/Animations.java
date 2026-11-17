package com.soa.desktop.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class Animations {

    // 1. Smooth Fade-In for switching tabs
    public static void fadeIn(Node node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        ft.setToValue(1.0);
        ft.play();
    }

    // 2. Card Hover Lift (Modern Micro-interaction)
    public static void addHoverLift(Node node) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), node);
        scaleUp.setToX(1.02);
        scaleUp.setToY(1.02);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), node);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        node.setOnMouseEntered(e -> scaleUp.playFromStart());
        node.setOnMouseExited(e -> scaleDown.playFromStart());
    }

    // 3. Spring-Loaded Number Counter
    public static void animateNumber(Label label, double from, double to) {
        // Use a Timeline to interpolate the value
        final double[] current = {from};
        Timeline timeline = new Timeline();
        
        // We use 30 frames for smooth animation
        int frames = 30;
        for (int i = 1; i <= frames; i++) {
            final double progress = (double) i / frames;
            // Ease-out cubic formula for spring feel
            double eased = 1 - Math.pow(1 - progress, 3);
            double nextVal = from + (to - from) * eased;
            
            KeyFrame kf = new KeyFrame(Duration.millis(i * 10), e -> {
                label.setText(String.format("%.0f", nextVal));
            });
            timeline.getKeyFrames().add(kf);
        }
        timeline.play();
    }

    // 4. Row Stagger Animation for Lists/Tables
    public static void staggerRows(javafx.scene.Node parent) {
        if (parent instanceof javafx.scene.layout.VBox || parent instanceof javafx.scene.layout.HBox) {
            javafx.scene.layout.Pane pane = (javafx.scene.layout.Pane) parent;
            int delay = 0;
            for (Node child : pane.getChildren()) {
                child.setOpacity(0);
                child.setTranslateY(10);
                
                ParallelTransition pt = new ParallelTransition();
                FadeTransition ft = new FadeTransition(Duration.millis(300), child);
                ft.setToValue(1.0);
                TranslateTransition tt = new TranslateTransition(Duration.millis(300), child);
                tt.setToY(0);
                pt.getChildren().addAll(ft, tt);
                
                pt.setDelay(Duration.millis(delay));
                pt.play();
                delay += 40; // 40ms stagger
            }
        }
    }
}