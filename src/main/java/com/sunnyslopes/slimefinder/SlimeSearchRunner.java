package com.sunnyslopes.slimefinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Slime chunk search via JNI ({@link SlimeFinderBridge}).
 */
public class SlimeSearchRunner {

    public static final int RING_INNER = 24;
    public static final int RING_OUTER = 128;
    public static final int PHASE1_GRID_STEP = 16;

    public static final double SLIME_AREA_FULL = Math.PI * (128 * 128 - 24 * 24);
    public static final double MIN_MIN_RATIO = 10.0;
    public static final double DEFAULT_MIN_RATIO = 20.0;
    public static final double MAX_MIN_RATIO = 40.0;
    public static final int MIN_MIN_SLIME_AREA = (int) Math.round(SLIME_AREA_FULL * 0.10);
    public static final int DEFAULT_MIN_SLIME_AREA = (int) Math.round(SLIME_AREA_FULL * 0.20);
    public static final int MAX_MIN_SLIME_AREA = (int) Math.round(SLIME_AREA_FULL * 0.40);

    public static final long MAX_SEARCH_AREA_BLOCKS = 30_000_000L * 30_000_000L;
    public static final float DEFAULT_PRESERVE_RANGE = 0.9f;
    public static final int OUTPUT_REGION_SIZE = 64;

    /** cubiomes MCVersion values */
    public static final int MC_1_18 = 22;
    public static final int MC_26_1 = 33;
    public static final int MC_NEWEST = 34;

    private static final long PROGRESS_POLL_MS = 100L;

    static List<int[]> filterResultsForOutput(int[] raw, boolean biomeConversion) {
        if (raw == null || raw.length < 3) {
            return List.of();
        }
        int stride = resolveResultStride(raw, biomeConversion);
        Map<Long, int[]> byXZ = new HashMap<>();
        for (int i = 0; i + stride - 1 < raw.length; i += stride) {
            int x = raw[i];
            int z = raw[i + 1];
            int[] entry = new int[stride];
            System.arraycopy(raw, i, entry, 0, stride);
            long pk = packXZ(x, z);
            byXZ.merge(pk, entry, (a, b) -> compareArea(a, b, biomeConversion) >= 0 ? a : b);
        }
        Map<Long, int[]> byCell = new HashMap<>();
        for (int[] h : byXZ.values()) {
            int bx = Math.floorDiv(h[0], OUTPUT_REGION_SIZE);
            int bz = Math.floorDiv(h[1], OUTPUT_REGION_SIZE);
            long ck = packXZ(bx, bz);
            byCell.merge(ck, h, (a, b) -> compareArea(a, b, biomeConversion) >= 0 ? a : b);
        }
        List<int[]> out = new ArrayList<>(byCell.values());
        out.sort((a, b) -> Integer.compare(compareArea(b, a, biomeConversion), compareArea(a, b, biomeConversion)));
        return out;
    }

    private static int resolveResultStride(int[] raw, boolean biomeConversion) {
        if (!biomeConversion) {
            return 3;
        }
        if (raw.length >= 4 && raw.length % 4 == 0) {
            return 4;
        }
        return 3;
    }

    private static int compareArea(int[] a, int[] b, boolean biomeConversion) {
        int aa = biomeConversion && a.length >= 4 ? a[3] : a[2];
        int bb = biomeConversion && b.length >= 4 ? b[3] : b[2];
        return Integer.compare(aa, bb);
    }

    private static final String CONVERTED_MARKER_ZH = "折算后:";
    private static final String CONVERTED_MARKER_EN = "Converted:";

    /** Parse the area used for sorting from a result line. */
    public static int parseSortAreaFromLine(String line, boolean useConvertedArea) {
        if (useConvertedArea) {
            int idx = line.indexOf(CONVERTED_MARKER_ZH);
            if (idx < 0) {
                idx = line.indexOf(CONVERTED_MARKER_EN);
            }
            if (idx >= 0) {
                int colon = line.indexOf(':', idx);
                int slash = line.indexOf('/', idx);
                if (colon >= 0 && slash > colon) {
                    try {
                        return Integer.parseInt(line.substring(colon + 1, slash).trim());
                    } catch (NumberFormatException ignored) {
                        // fall through to raw area
                    }
                }
            }
        }
        return parseRawAreaFromLine(line);
    }

    public static int parseRawAreaFromLine(String line) {
        int sIdx = line.indexOf("s=");
        if (sIdx < 0) {
            return -1;
        }
        int slashIdx = line.indexOf("/", sIdx + 2);
        if (slashIdx < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(line.substring(sIdx + 2, slashIdx).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public record ProgressInfo(int phase, long processed, long total, double percentage, long elapsedMs, long remainingMs,
                               int currentSeedIndex, int totalSeeds, boolean done, boolean refiningResults,
                               boolean nativePauseSettled, boolean nativeStopRequested) {
        public static ProgressInfo of(int phase, long processed, long total, long elapsedMs, long remainingMs) {
            return of(phase, processed, total, elapsedMs, remainingMs, false, false, false);
        }

        public static ProgressInfo of(int phase, long processed, long total, long elapsedMs, long remainingMs,
                                      boolean refiningResults) {
            return of(phase, processed, total, elapsedMs, remainingMs, refiningResults, false, false);
        }

        public static ProgressInfo of(int phase, long processed, long total, long elapsedMs, long remainingMs,
                                      boolean refiningResults, boolean nativePauseSettled, boolean nativeStopRequested) {
            double pct = total > 0 ? (processed * 100.0 / total) : 0;
            return new ProgressInfo(phase, processed, total, pct, elapsedMs, remainingMs, 0, 0, false, refiningResults,
                nativePauseSettled, nativeStopRequested);
        }

        public static ProgressInfo terminal(long elapsedMs, long processed, long total) {
            long t = Math.max(1, total);
            long p = Math.min(processed, t);
            double pct = t > 0 ? (p * 100.0 / t) : 100.0;
            return new ProgressInfo(0, p, t, pct, elapsedMs, 0, 0, 0, true, false, false, false);
        }
    }

    public static boolean isNativePauseSettled(int[] progress) {
        if (progress == null || progress.length < 4) {
            return false;
        }
        boolean tryPause = progress.length > 4 && progress[4] != 0;
        if (!tryPause) {
            return false;
        }
        int phase = progress[2];
        int status = progress[3];
        if (phase == 1) {
            return status == 1;
        }
        if (phase == 2) {
            return true;
        }
        return true;
    }

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile long runStartTimeMs = 0;
    private volatile long totalPausedMs = 0;
    private volatile long pauseStartMs = 0;

    public boolean isRunning() { return isRunning; }
    public boolean isPaused() { return isPaused; }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            pauseStartMs = System.currentTimeMillis();
            try {
                SlimeFinderBridge.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    public void resume() {
        if (isPaused) {
            if (pauseStartMs > 0) {
                totalPausedMs += System.currentTimeMillis() - pauseStartMs;
                pauseStartMs = 0;
            }
            isPaused = false;
            try {
                SlimeFinderBridge.resume();
            } catch (Throwable ignored) {
            }
        }
    }

    public void stop() {
        try {
            SlimeFinderBridge.stop();
        } catch (Throwable ignored) {
        }
    }

    private long getElapsedMs() {
        long base = System.currentTimeMillis() - runStartTimeMs;
        long paused = totalPausedMs;
        if (pauseStartMs > 0) {
            paused += System.currentTimeMillis() - pauseStartMs;
        }
        return Math.max(0, base - paused);
    }

    public void startSlimeSearch(long seed, int minX, int maxX, int minZ, int maxZ, int minArea,
                                 int mcVersion, int versionTier, boolean biomeConversion,
                                 int threadCount, String convertedAreaLabel,
                                 Consumer<ProgressInfo> progressCallback, Consumer<String> resultCallback) {
        if (isRunning) return;
        Thread t = new Thread(() -> runSlimeSearch(seed, minX, maxX, minZ, maxZ, minArea,
            mcVersion, versionTier, biomeConversion, threadCount, convertedAreaLabel,
            progressCallback, resultCallback),
            "slimefinder-search");
        t.setDaemon(true);
        t.start();
    }

    public void runSlimeSearchBlocking(long seed, int minX, int maxX, int minZ, int maxZ, int minArea,
                                       int mcVersion, int versionTier, boolean biomeConversion,
                                       int threadCount, String convertedAreaLabel,
                                       Consumer<ProgressInfo> progressCallback, Consumer<String> resultCallback) {
        if (isRunning) return;
        runSlimeSearch(seed, minX, maxX, minZ, maxZ, minArea, mcVersion, versionTier, biomeConversion,
            threadCount, convertedAreaLabel, progressCallback, resultCallback);
    }

    private void runSlimeSearch(long seed, int minX, int maxX, int minZ, int maxZ, int minArea,
                                int mcVersion, int versionTier, boolean biomeConversion,
                                int threadCount, String convertedAreaLabel,
                                Consumer<ProgressInfo> progressCallback, Consumer<String> resultCallback) {
        isRunning = true;
        isPaused = false;
        totalPausedMs = 0;
        pauseStartMs = 0;
        runStartTimeMs = System.currentTimeMillis();
        int threads = Math.max(1, Math.min(threadCount, Runtime.getRuntime().availableProcessors()));

        long searchWidth = (long) (maxX - minX) + 1;
        long searchHeight = (long) (maxZ - minZ) + 1;
        long searchArea = searchWidth * searchHeight;
        if (searchArea <= 0 || searchArea > MAX_SEARCH_AREA_BLOCKS) {
            isRunning = false;
            if (resultCallback != null && searchArea > MAX_SEARCH_AREA_BLOCKS) {
                resultCallback.accept("[Error] Search area too large (" + searchArea + " blocks).");
            }
            if (progressCallback != null) {
                progressCallback.accept(ProgressInfo.terminal(getElapsedMs(), 0, 1));
            }
            return;
        }

        int startX = minX - RING_OUTER;
        int startZ = minZ - RING_OUTER;
        int width = (maxX - minX + 1) + 2 * RING_OUTER;
        int height = (maxZ - minZ + 1) + 2 * RING_OUTER;

        AtomicInteger phase1TotalMax = new AtomicInteger(0);
        AtomicLong lastProcessed = new AtomicLong(0);
        AtomicLong lastTotal = new AtomicLong(1);

        ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "slimefinder-progress");
            t.setDaemon(true);
            return t;
        });

        if (progressCallback != null) {
            progressCallback.accept(ProgressInfo.of(0, 0, 1, getElapsedMs(), 0));
        }

        progressScheduler.scheduleAtFixedRate(() -> {
            if (progressCallback == null || !isRunning) {
                return;
            }
            try {
                int[] arr = SlimeFinderBridge.getSearchProgress();
                if (arr == null || arr.length < 4) {
                    return;
                }
                boolean tryStop = arr.length > 5 && arr[5] != 0;
                boolean pauseSettled = isNativePauseSettled(arr);
                int cur = arr[0];
                int tot = arr[1];
                int nPhase = arr[2];

                long processed;
                long total;
                if (nPhase == 1) {
                    phase1TotalMax.set(Math.max(phase1TotalMax.get(), tot));
                    processed = cur;
                    total = Math.max(tot, 1L);
                } else if (nPhase == 2) {
                    int p1 = phase1TotalMax.get();
                    if (p1 <= 0) {
                        processed = cur;
                        total = Math.max(tot, 1L);
                    } else {
                        processed = (long) p1 + cur;
                        total = (long) p1 + Math.max(tot, 1);
                    }
                } else {
                    processed = cur;
                    total = Math.max(tot, 1L);
                }

                lastProcessed.set(processed);
                lastTotal.set(total);

                long elapsed = getElapsedMs();
                long remaining = 0L;
                if (!isPaused && total > processed && processed > 0) {
                    remaining = elapsed * (total - processed) / processed;
                }
                boolean refining = (nPhase == 2);
                progressCallback.accept(ProgressInfo.of(0, processed, total, elapsed, remaining, refining, pauseSettled, tryStop));
            } catch (Throwable ignored) {
            }
        }, 0, PROGRESS_POLL_MS, TimeUnit.MILLISECONDS);

        try {
            int[] raw = SlimeFinderBridge.slimeSearch(seed, startX, startZ, width, height,
                mcVersion, versionTier, minArea, DEFAULT_PRESERVE_RANGE, biomeConversion ? 1 : 0, threads);

            if (raw != null && resultCallback != null) {
                int fullAreaInt = (int) SLIME_AREA_FULL;
                String convLabel = (convertedAreaLabel != null && !convertedAreaLabel.isEmpty())
                    ? convertedAreaLabel : CONVERTED_MARKER_ZH;
                StringBuilder sb = new StringBuilder();
                for (int[] hit : filterResultsForOutput(raw, biomeConversion)) {
                    int x = hit[0];
                    int z = hit[1];
                    int rawArea = hit[2];
                    double rawPct = rawArea / SLIME_AREA_FULL * 100.0;
                    if (biomeConversion && hit.length >= 4) {
                        int converted = hit[3];
                        double convPct = converted / SLIME_AREA_FULL * 100.0;
                        sb.append(String.format("/tp %d 64 %d s=%d/%d = %.2f%% | %s %d/%d = %.2f%%",
                            x, z, rawArea, fullAreaInt, rawPct, convLabel, converted, fullAreaInt, convPct));
                    } else {
                        sb.append(String.format("/tp %d 64 %d s=%d/%d = %.2f%%",
                            x, z, rawArea, fullAreaInt, rawPct));
                    }
                    sb.append('\n');
                }
                if (sb.length() > 0) {
                    resultCallback.accept(sb.toString());
                }
            }
        } catch (UnsatisfiedLinkError e) {
            if (resultCallback != null) {
                resultCallback.accept("[Error] Native library not loaded. Build with: gradle buildNative. " + e.getMessage());
            }
        } finally {
            progressScheduler.shutdownNow();
            try {
                progressScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            isRunning = false;
            if (pauseStartMs > 0) {
                totalPausedMs += System.currentTimeMillis() - pauseStartMs;
                pauseStartMs = 0;
            }
            isPaused = false;
            if (progressCallback != null) {
                long p = lastProcessed.get();
                long t = lastTotal.get();
                if (t <= 0) {
                    t = 1;
                }
                if (p < t) {
                    p = t;
                }
                progressCallback.accept(ProgressInfo.terminal(getElapsedMs(), p, t));
            }
        }
    }
}
