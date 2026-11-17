package com.soa.desktop.ui.motion;

import javafx.animation.Interpolator;

public final class SpringInterpolator extends Interpolator {

    private final double stiffness;
    private final double damping;
    private final double endpoint;

    public SpringInterpolator(double stiffness, double damping) {
        if (stiffness <= 0 || damping <= 0) {
            throw new IllegalArgumentException("Spring stiffness and damping must be positive");
        }
        this.stiffness = stiffness;
        this.damping = damping;
        endpoint = 1 - Math.exp(-damping)
                * (Math.cos(stiffness) + (damping / stiffness) * Math.sin(stiffness));
        if (Math.abs(endpoint) < 1e-6) {
            throw new IllegalArgumentException("Spring parameters produce an unstable endpoint");
        }
    }

    @Override
    protected double curve(double t) {
        if (t <= 0 || t >= 1) return t;
        double response = 1 - Math.exp(-damping * t)
                * (Math.cos(stiffness * t) + (damping / stiffness) * Math.sin(stiffness * t));
        return response / endpoint;
    }
}
