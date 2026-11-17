package com.soa.desktop.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainAppTest {

    @Test
    void enablesDemoFromJavafxArgumentEnvironmentOrSystemProperty() {
        assertTrue(MainApp.demoEnabled(List.of("--demo"), null, null));
        assertTrue(MainApp.demoEnabled(List.of(), null, "true"));
        assertTrue(MainApp.demoEnabled(List.of(), "TRUE", null));
    }

    @Test
    void leavesDemoDisabledUnlessAnExplicitToggleIsTrue() {
        assertFalse(MainApp.demoEnabled(List.of(), null, null));
        assertFalse(MainApp.demoEnabled(List.of(), "false", "false"));
    }
}
