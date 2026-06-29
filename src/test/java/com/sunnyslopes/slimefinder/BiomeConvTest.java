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
        for (int i = 0; i + 3 < r.length; i += 4) {
            int raw = r[i + 2];
            int conv = r[i + 3];
            assertTrue(raw > 0, "raw at " + i);
            assertTrue(conv > 0 && conv <= raw, "raw=" + raw + " conv=" + conv);
        }
    }
}
