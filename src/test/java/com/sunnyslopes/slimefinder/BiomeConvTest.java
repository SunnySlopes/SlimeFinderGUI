package com.sunnyslopes.slimefinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeConvTest {

    @Test
    void convertedAreaNonZero() {
        int startX = -2000 - SlimeSearchRunner.RING_OUTER;
        int startZ = -2000 - SlimeSearchRunner.RING_OUTER;
        int width = 4001 + 2 * SlimeSearchRunner.RING_OUTER;
        int[] r = SlimeFinderBridge.slimeSearch(
            12345L, startX, startZ, width, width,
            SlimeSearchRunner.MC_NEWEST, SlimeFinderBridge.VERSION_TIER_26_2_PLUS,
            0, SlimeSearchRunner.DEFAULT_PRESERVE_RANGE, 1, 2);
        assertNotNull(r);
        assertTrue(r.length >= 4, "len=" + r.length);
        System.out.println("raw=" + r[2] + " conv=" + r[3]);
        assertTrue(r[3] > 0 && r[3] <= r[2], "raw=" + r[2] + " conv=" + r[3]);
    }
}
