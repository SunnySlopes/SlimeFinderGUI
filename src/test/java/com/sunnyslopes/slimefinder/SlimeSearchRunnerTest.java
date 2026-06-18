package com.sunnyslopes.slimefinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlimeSearchRunnerTest {

    @Test
    void filterResultsKeepsMaxPerRegion() {
        int[] raw = {0, 0, 100, 10, 10, 200, 70, 70, 150};
        var out = SlimeSearchRunner.filterResultsForOutput(raw, false);
        assertFalse(out.isEmpty());
        assertTrue(out.get(0)[2] >= 100);
    }

    @Test
    void defaultAreaConstants() {
        assertEquals(9932, SlimeSearchRunner.DEFAULT_MIN_SLIME_AREA);
        assertEquals(19865, SlimeSearchRunner.MAX_MIN_SLIME_AREA);
    }
}
