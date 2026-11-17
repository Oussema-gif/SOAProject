package com.soa.desktop.ui.components;

import com.soa.desktop.ui.motion.Motion;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;

public final class StatusChip extends Label {

    private static final PseudoClass SUCCESS = PseudoClass.getPseudoClass("success");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass CRITICAL = PseudoClass.getPseudoClass("critical");
    private static final PseudoClass OFFLINE = PseudoClass.getPseudoClass("offline");
    private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");

    public StatusChip(String text, String state) {
        super(text);
        getStyleClass().add("status-chip");
        setState(state);
    }

    public void setState(String state) {
        pseudoClassStateChanged(SUCCESS, "success".equalsIgnoreCase(state)
                || "normal".equalsIgnoreCase(state) || "running".equalsIgnoreCase(state)
                || "healthy".equalsIgnoreCase(state));
        pseudoClassStateChanged(WARNING, "warning".equalsIgnoreCase(state));
        pseudoClassStateChanged(CRITICAL, "critical".equalsIgnoreCase(state) || "failed".equalsIgnoreCase(state));
        pseudoClassStateChanged(OFFLINE, "offline".equalsIgnoreCase(state));
        pseudoClassStateChanged(LOADING, "loading".equalsIgnoreCase(state)
                || "connecting".equalsIgnoreCase(state) || "pending".equalsIgnoreCase(state)
                || "checking".equalsIgnoreCase(state));
        if (getScene() != null) {
            setScaleX(0.94);
            setScaleY(0.94);
            Motion.scale(this, 1);
        }
    }
}
