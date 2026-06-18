package com.sunnyslopes.slimefinder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JNI bridge to native slimefinder library.
 */
public final class SlimeFinderBridge {

    /** Version tier: 1.18 biomes */
    public static final int VERSION_TIER_1_18 = 0;
    /** Version tier: 1.19~26.1 biomes (includes deep_dark conversion) */
    public static final int VERSION_TIER_1_19_26_1 = 1;
    /** Version tier: 26.2+ biomes (includes sulfur_caves conversion) */
    public static final int VERSION_TIER_26_2_PLUS = 2;

    private static boolean loaded = false;

    static {
        loadNativeLibrary();
    }

    private SlimeFinderBridge() {}

    private static void loadNativeLibrary() {
        if (loaded) return;
        if (tryLoadFromBundledResources()) {
            loaded = true;
            return;
        }
        Path base = Paths.get(System.getProperty("user.dir", "."));
        List<String> libNames = getNativeLibNamesForCurrentOs();
        Path[] candidates = {
            base.resolve("native"),
            base.resolve("build").resolve("native"),
            base.resolve("build"),
        };
        for (Path dir : candidates) {
            for (String libName : libNames) {
                File f = dir.resolve(libName).toFile();
                if (f.exists()) {
                    System.load(f.getAbsolutePath());
                    loaded = true;
                    return;
                }
            }
        }
        try {
            System.loadLibrary("slimeFinderLibJ");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError(
                "slimeFinderLibJ native library not found. Build with: gradle buildNative. "
                    + e.getMessage());
        }
    }

    private static boolean tryLoadFromBundledResources() {
        String osFolder = getOsResourceFolder();
        if (osFolder == null) return false;
        for (String libName : getNativeLibNamesForCurrentOs()) {
            String resourcePath = "/native/" + osFolder + "/" + libName;
            if (tryLoadSingleBundledResource(resourcePath, libName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryLoadSingleBundledResource(String resourcePath, String libName) {
        try (InputStream is = SlimeFinderBridge.class.getResourceAsStream(resourcePath)) {
            if (is == null) return false;
            Path tempDir = Files.createTempDirectory("slimefinder-native-");
            Path tempLib = tempDir.resolve(libName);
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            System.load(tempLib.toAbsolutePath().toString());
            return true;
        } catch (IOException | UnsatisfiedLinkError ex) {
            return false;
        }
    }

    private static String getOsResourceFolder() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        if (os.contains("nix") || os.contains("nux") || os.contains("aix") || os.contains("linux")) return "linux";
        return null;
    }

    private static List<String> getNativeLibNamesForCurrentOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> names = new ArrayList<>();
        if (os.contains("win")) {
            names.add("libslimeFinderLibJ.dll");
            names.add("slimeFinderLibJ.dll");
        } else if (os.contains("mac")) {
            names.add("libslimeFinderLibJ.dylib");
        } else {
            names.add("libslimeFinderLibJ.so");
        }
        return names;
    }

    /**
     * Slime chunk ring search (annulus 24-128).
     *
     * @param mcVersion cubiomes MCVersion enum value
     * @param versionTier {@link #VERSION_TIER_1_18}, {@link #VERSION_TIER_1_19_26_1}, or {@link #VERSION_TIER_26_2_PLUS}
     * @param biomeConversion when true, filter by converted area and return raw+converted per hit
     * @return without conversion: [x, z, area, ...]; with conversion: [x, z, rawArea, convertedArea, ...]
     */
    public static native int[] slimeSearch(long seed, int startX, int startZ,
        int width, int height, int mcVersion, int versionTier, int minArea,
        float preserveRange, int biomeConversion, int numThreads);

    /** [current, total, phase, status, tryPause, tryStop] */
    public static native int[] getSearchProgress();

    public static native int[] getNowResult();

    public static native boolean pause();

    public static native boolean resume();

    public static native boolean stop();
}
