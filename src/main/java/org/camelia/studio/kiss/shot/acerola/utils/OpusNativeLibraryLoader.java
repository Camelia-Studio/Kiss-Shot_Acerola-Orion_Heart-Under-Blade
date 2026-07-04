package org.camelia.studio.kiss.shot.acerola.utils;

import club.minnced.opus.util.OpusLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class OpusNativeLibraryLoader {
    private static final Logger logger = LoggerFactory.getLogger(OpusNativeLibraryLoader.class);

    private OpusNativeLibraryLoader() {
    }

    public static void preloadFromEnvironment() {
        String configuredPath = Configuration.getInstance().getDotenv().get("OPUS_LIBRARY_PATH", "");
        List<Path> candidates = candidatePaths(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                configuredPath);

        for (Path candidate : candidates) {
            if (!Files.exists(candidate)) {
                continue;
            }

            try {
                OpusLibrary.loadFrom(candidate.toAbsolutePath().toString());
                logger.info("Bibliothèque Opus native chargée depuis {}", candidate);
                return;
            } catch (UnsatisfiedLinkError e) {
                logger.warn("Impossible de charger la bibliothèque Opus native {}", candidate, e);
            }
        }
    }

    static List<Path> candidatePaths(String osName, String osArch, String configuredPath) {
        List<Path> paths = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            paths.add(Path.of(configuredPath));
        }

        if (isAppleSilicon(osName, osArch)) {
            paths.add(Path.of("/opt/homebrew/lib/libopus.dylib"));
            paths.add(Path.of("/opt/homebrew/opt/opus/lib/libopus.dylib"));
        }

        return List.copyOf(paths);
    }

    private static boolean isAppleSilicon(String osName, String osArch) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase();
        String normalizedArch = osArch == null ? "" : osArch.toLowerCase();
        return normalizedOs.contains("mac")
                && (normalizedArch.equals("aarch64")
                || normalizedArch.equals("arm64")
                || normalizedArch.startsWith("arm64e"));
    }
}
