package com.soa.desktop.ui.components;

import com.soa.desktop.ui.motion.Motion;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class TelemetryChart extends Region {

    private static final int CAPACITY = 72;
    private final ArrayDeque<Double> samples = new ArrayDeque<>();
    private final Path line = new Path();
    private final Path fill = new Path();
    private final Circle latestPoint = new Circle(4);
    private final List<Region> guides = new ArrayList<>();

    public TelemetryChart() {
        getStyleClass().add("telemetry-chart");
        line.getStyleClass().add("chart-line");
        fill.getStyleClass().add("chart-fill");
        latestPoint.getStyleClass().add("chart-point");
        getChildren().addAll(fill, line, latestPoint);
        for (int i = 0; i < 4; i++) {
            Region guide = new Region();
            guide.getStyleClass().add("chart-guide");
            guides.add(guide);
            getChildren().add(guide);
        }
        setMinHeight(190);
        setPrefHeight(220);
    }

    public void addSample(double value) {
        samples.addLast(value);
        while (samples.size() > CAPACITY) samples.removeFirst();
        redraw();
    }

    public void clearSamples() {
        samples.clear();
        line.getElements().clear();
        fill.getElements().clear();
        latestPoint.setVisible(false);
    }

    @Override
    protected void layoutChildren() {
        redraw();
    }

    private void redraw() {
        if (samples.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;
        double left = 8;
        double top = 14;
        double width = getWidth() - 16;
        double height = getHeight() - 28;
        double min = samples.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = samples.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double range = Math.max(1, max - min);
        line.getElements().clear();
        fill.getElements().clear();
        int index = 0;
        double x = left;
        double y = top;
        for (double sample : samples) {
            x = left + width * index / Math.max(1, samples.size() - 1);
            y = top + height - ((sample - min) / range) * height;
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
        for (int i = 0; i < guides.size(); i++) {
            guides.get(i).resizeRelocate(left, top + height * i / (guides.size() - 1), width, 1);
        }
    }
}
