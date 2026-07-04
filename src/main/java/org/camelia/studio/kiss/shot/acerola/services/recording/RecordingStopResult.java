package org.camelia.studio.kiss.shot.acerola.services.recording;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record RecordingStopResult(
        String guildId,
        String channelName,
        RecordingMode mode,
        Instant startedAt,
        Instant stoppedAt,
        List<RecordingArtifact> files) {
    public Duration duration() {
        return Duration.between(startedAt, stoppedAt);
    }

    public void deleteFilesQuietly() {
        for (RecordingArtifact file : files) {
            try {
                Files.deleteIfExists(file.path());
            } catch (IOException ignored) {
                // Temporary Discord upload files are best-effort cleanup.
            }
        }
    }
}
