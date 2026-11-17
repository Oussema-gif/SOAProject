package com.soa.desktop.ui.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringInterpolatorTest {

    @Test
    void startsAndEndsAtTheRequestedValuesWhileOvershootingInBetween() {
        SpringInterpolator spring = new SpringInterpolator(10, 8);

        assertEquals(0.0, spring.interpolate(0.0, 1.0, 0), 0.0001);
        assertEquals(1.0, spring.interpolate(0.0, 1.0, 1), 0.0001);
        double overshoot = spring.interpolate(0.0, 1.0, 0.3);
        assertTrue(overshoot > 1 && overshoot < 1.1);
    }

    @Test
    void rejectsNonPositiveSpringParameters() {
        assertThrows(IllegalArgumentException.class, () -> new SpringInterpolator(0, 6));
        assertThrows(IllegalArgumentException.class, () -> new SpringInterpolator(12, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpringInterpolator(2 * Math.PI, 1e-10));
    }
}
