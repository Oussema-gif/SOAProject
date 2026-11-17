package com.soa.desktop.ui.motion;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.List;

public final class Motion {

    private static volatile boolean reducedMotion;
    private static final Interpolator SPRING = new SpringInterpolator(10, 8);

    private Motion() { }

    public static boolean isReducedMotion() {
        return reducedMotion;
    }

    public static void setReducedMotion(boolean reduced) {
        reducedMotion = reduced;
    }

    public static Interpolator interpolator() {
        return reducedMotion ? Interpolator.LINEAR : SPRING;
    }

    public static Animation enter(Node node) {
        if (reducedMotion) {
            FadeTransition fade = new FadeTransition(Duration.millis(120), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.LINEAR);
            return fade;
        }
        node.setOpacity(0);
        node.setTranslateY(18);
        FadeTransition fade = new FadeTransition(Duration.millis(520), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition slide = new TranslateTransition(Duration.millis(520), node);
        slide.setFromY(18);
        slide.setToY(0);
        slide.setInterpolator(SPRING);
        return new ParallelTransition(fade, slide);
    }

    public static void stagger(List<? extends Node> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            Animation animation = enter(nodes.get(i));
            animation.setDelay(Duration.millis(reducedMotion ? 0 : i * 55L));
            animation.play();
        }
    }

    public static void tween(DoubleProperty property, double value, Duration duration) {
        new javafx.animation.Timeline(new javafx.animation.KeyFrame(duration,
                new javafx.animation.KeyValue(property, value, interpolator()))).play();
    }

    public static void scale(Node node, double scale) {
        ScaleTransition transition = new ScaleTransition(Duration.millis(reducedMotion ? 110 : 230), node);
        transition.setToX(scale);
        transition.setToY(scale);
        transition.setInterpolator(interpolator());
        transition.play();
    }
}
