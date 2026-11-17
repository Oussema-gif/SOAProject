package com.soa.desktop.ui.components;

import com.soa.desktop.ui.motion.Motion;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.Locale;

public final class MetricCard extends VBox {

    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass OFFLINE = PseudoClass.getPseudoClass("offline");
    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");

    private final Label valueLabel = new Label("--");
    private final SimpleDoubleProperty tweenedValue = new SimpleDoubleProperty(0);
    private double lastValue;
    private boolean hasValue;
    private double warningThreshold;
    private double criticalThreshold;
    private Timeline numberTween;
    private Timeline borderTween;
    private Timeline bloomTween;

    public MetricCard(String title, String unit, double warningThreshold, double criticalThreshold) {
        this.warningThreshold = warningThreshold;
        this.criticalThreshold = criticalThreshold;
        getStyleClass().add("metric-card");
        setSpacing(10);
        setPadding(new Insets(18));
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-label");
        valueLabel.getStyleClass().add("metric-value");
        Label unitLabel = new Label(unit);
        unitLabel.getStyleClass().add("metric-unit");
        getChildren().addAll(titleLabel, valueLabel, unitLabel);
        tweenedValue.addListener((observable, oldValue, newValue) ->
                valueLabel.setText(String.format(Locale.ROOT, "%.2f", newValue.doubleValue())));
        setStatus("success");
    }

    public void updateValue(double value) {
        updateValue(value, value > criticalThreshold ? "critical" : value > warningThreshold ? "warning" : "success");
    }

    public void updateValue(double value, String status) {
        if (numberTween != null) numberTween.stop();
        if (hasValue && !Motion.isReducedMotion()) {
            tweenedValue.set(lastValue);
            numberTween = new Timeline(new KeyFrame(Duration.millis(460),
                    new KeyValue(tweenedValue, value, Motion.interpolator())));
            numberTween.play();
        } else {
            tweenedValue.set(value);
        }
        lastValue = value;
        hasValue = true;
        setStatus(status);
    }

    public void setOffline() {
        setStatus("offline");
        valueLabel.setText("--");
        hasValue = false;
    }

    public void setThresholds(double warning, double critical) {
        warningThreshold = warning;
        criticalThreshold = critical;
        if (hasValue) updateValue(lastValue);
    }

    private void setStatus(String status) {
        pseudoClassStateChanged(SUCCESS, "success".equals(status));
        pseudoClassStateChanged(WARNING, "warning".equals(status));
        pseudoClassStateChanged(CRITICAL, "critical".equals(status));
        pseudoClassStateChanged(OFFLINE, "offline".equals(status));
        if (borderTween != null) borderTween.stop();
        if (bloomTween != null) bloomTween.stop();
        if (getScene() != null) {
            applyCss();
            if (getEffect() instanceof DropShadow shadow) {
                bloomTween = new Timeline(new KeyFrame(Duration.millis(Motion.isReducedMotion() ? 120 : 380),
                        new KeyValue(shadow.radiusProperty(), "critical".equals(status) ? 28 : "warning".equals(status) ? 20 : 0, Motion.interpolator()),
                        new KeyValue(shadow.spreadProperty(), "critical".equals(status) ? 0.18 : "warning".equals(status) ? 0.1 : 0, Motion.interpolator())));
                bloomTween.play();
            }
            animateBorder(status);
        }
    }

    private void animateBorder(String status) {
        Border border = getBorder();
        if (border == null || border.getStrokes().isEmpty()) return;
        BorderStroke stroke = border.getStrokes().get(0);
        Paint paint = stroke.getTopStroke();
        final Paint borderPaint = paint == null ? Color.TRANSPARENT : paint;
        double target = "critical".equals(status) ? 2.7 : "warning".equals(status) ? 2.2 : 1;
        SimpleDoubleProperty width = new SimpleDoubleProperty(stroke.getWidths().getTop());
        width.addListener((observable, oldValue, newValue) -> setBorder(new Border(new BorderStroke(
                borderPaint, borderPaint, borderPaint, borderPaint,
                stroke.getTopStyle(), stroke.getRightStyle(), stroke.getBottomStyle(), stroke.getLeftStyle(),
                stroke.getRadii(), new BorderWidths(newValue.doubleValue()), stroke.getInsets()))));
        borderTween = new Timeline(new KeyFrame(Duration.millis(Motion.isReducedMotion() ? 120 : 380),
                new KeyValue(width, target, Motion.interpolator())));
        borderTween.play();
    }
}
