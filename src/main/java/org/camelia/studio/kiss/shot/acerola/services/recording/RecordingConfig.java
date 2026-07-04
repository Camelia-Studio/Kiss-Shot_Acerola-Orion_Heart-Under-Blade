package org.camelia.studio.kiss.shot.acerola.services.recording;

import org.camelia.studio.kiss.shot.acerola.utils.Configuration;

import java.nio.file.Path;
import java.time.Duration;

public record RecordingConfig(
        Path tempDirectory,
        String ffmpegCommand,
        int mp3BitrateKbps,
        Duration emptyChannelTimeout) {
    private static final int DEFAULT_EMPTY_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_MP3_BITRATE_KBPS = 96;

    public static RecordingConfig fromEnv() {
        return new RecordingConfig(
                resolveTempDirectory(),
                get("RECORDING_FFMPEG_COMMAND", "ffmpeg"),
                positiveInt("RECORDING_MP3_BITRATE_KBPS", DEFAULT_MP3_BITRATE_KBPS),
                Duration.ofSeconds(positiveInt("RECORDING_EMPTY_TIMEOUT_SECONDS", DEFAULT_EMPTY_TIMEOUT_SECONDS)));
    }

    private static Path resolveTempDirectory() {
        String configured = get("RECORDING_TEMP_DIR", "");
        if (configured == null || configured.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"), "kiss-shot-acerola-recordings");
        }
        return Path.of(configured);
    }

    private static int positiveInt(String key, int defaultValue) {
        String raw = get(key, String.valueOf(defaultValue));
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String get(String key, String defaultValue) {
        return Configuration.getInstance().getDotenv().get(key, defaultValue);
    }
}
