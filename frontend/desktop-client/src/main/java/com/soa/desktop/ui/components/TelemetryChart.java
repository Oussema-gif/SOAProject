package com.soa.desktop.ui.components;

import com.soa.desktop.ui.motion.Motion;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TelemetryChart extends Region {

    private static final int CAPACITY = 72;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private record Sample(double value, Instant timestamp) { }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final Path line = new Path();
    private final Path fill = new Path();
    private final Circle latestPoint = new Circle(4);
    private final Label emptyState = new Label("NO TELEMETRY SAMPLES");
    private final List<Region> guides = new ArrayList<>();
    private final Region warningLine = new Region();
    private final Region criticalLine = new Region();
    private final Tooltip tooltip = new Tooltip();
    private double warningThreshold = Double.NaN;
    private double criticalThreshold = Double.NaN;

    public TelemetryChart() {
        getStyleClass().add("telemetry-chart");
        line.getStyleClass().add("chart-line");
        fill.getStyleClass().add("chart-fill");
        latestPoint.getStyleClass().add("chart-point");
        warningLine.getStyleClass().add("chart-warning-line");
        criticalLine.getStyleClass().add("chart-critical-line");
        emptyState.getStyleClass().add("chart-empty-state");
        getChildren().addAll(fill, line, warningLine, criticalLine, latestPoint, emptyState);
        for (int i = 0; i < 4; i++) {
            Region guide = new Region();
            guide.getStyleClass().add("chart-guide");
            guides.add(guide);
            getChildren().add(guide);
        }
        setMinHeight(190);
        setPrefHeight(220);
        Tooltip.install(this, tooltip);
        setOnMouseMoved(event -> updateTooltip(event.getX()));
    }

    public void addSample(double value) {
        addSample(value, Instant.now());
    }

    public void addSample(double value, Instant timestamp) {
        samples.addLast(new Sample(value, timestamp));
        while (samples.size() > CAPACITY) samples.removeFirst();
        redraw();
    }

    public void setThresholds(double warning, double critical) {
        warningThreshold = warning;
        criticalThreshold = critical;
        redraw();
    }

    public void clearSamples() {
        samples.clear();
        line.getElements().clear();
        fill.getElements().clear();
        latestPoint.setVisible(false);
        warningLine.setVisible(false);
        criticalLine.setVisible(false);
        emptyState.setVisible(true);
    }

    @Override
    protected void layoutChildren() {
        emptyState.autosize();
        emptyState.relocate((getWidth() - emptyState.getWidth()) / 2,
                (getHeight() - emptyState.getHeight()) / 2);
        redraw();
    }

    private void redraw() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        emptyState.setVisible(samples.isEmpty());
        if (samples.isEmpty()) return;
        double left = 8;
        double top = 14;
        double width = getWidth() - 16;
        double height = getHeight() - 28;
        double min = samples.stream().mapToDouble(Sample::value).min().orElse(0);
        double max = samples.stream().mapToDouble(Sample::value).max().orElse(1);
        if (Double.isFinite(warningThreshold)) min = Math.min(min, warningThreshold);
        if (Double.isFinite(criticalThreshold)) max = Math.max(max, criticalThreshold);
        double range = Math.max(1, max - min);
        line.getElements().clear();
        fill.getElements().clear();
        int index = 0;
        double x = left;
        double y = top;
        for (Sample sample : samples) {
            x = left + width * index / Math.max(1, samples.size() - 1);
            y = top + height - ((sample.value() - min) / range) * height;
            if (index == 0) {
                line.getElements().add(new MoveTo(x, y));
                fill.getElements().add(new MoveTo(x, top + height));
                fill.getElements().add(new LineTo(x, y));
            } else {
                line.getElements().add(new LineTo(x, y));
                fill.getElements().add(new LineTo(x, y));
            }
            index++;
        }
        fill.getElements().add(new LineTo(x, top + height));
        fill.getElements().add(new LineTo(left, top + height));
        latestPoint.setCenterX(x);
        latestPoint.setCenterY(y);
        latestPoint.setVisible(true);
        latestPoint.setScaleX(0.72);
        latestPoint.setScaleY(0.72);
        Motion.scale(latestPoint, 1);
        positionThreshold(warningLine, warningThreshold, min, range, top, height, left, width);
        positionThreshold(criticalLine, criticalThreshold, min, range, top, height, left, width);
        for (int i = 0; i < guides.size(); i++) {
            guides.get(i).resizeRelocate(left, top + height * i / (guides.size() - 1), width, 1);
        }
    }

    private static void positionThreshold(Region marker, double threshold, double min, double range,
                                          double top, double height, double left, double width) {
        boolean visible = Double.isFinite(threshold) && threshold >= min && threshold <= min + range;
        marker.setVisible(visible);
        if (visible) marker.resizeRelocate(left, top + height - ((threshold - min) / range) * height,
                width, 1);
    }

    private void updateTooltip(double mouseX) {
        if (samples.isEmpty() || getWidth() <= 16) return;
        List<Sample> values = List.copyOf(samples);
        double plotWidth = getWidth() - 16;
        int index = (int) Math.round(Math.max(0, Math.min(1, (mouseX - 8) / plotWidth)) * (values.size() - 1));
        Sample sample = values.get(index);
        tooltip.setText(String.format(Locale.ROOT, "%.2f · %s", sample.value(), TIME_FORMAT.format(sample.timestamp())));
    }
}
