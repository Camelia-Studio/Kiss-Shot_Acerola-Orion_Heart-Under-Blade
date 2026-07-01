package org.camelia.studio.kiss.shot.acerola.services.recording;

import java.time.Duration;
import java.time.Instant;

public record RecordingStatus(
        String channelName,
        RecordingMode mode,
        Instant startedAt,
        Duration duration,
        int fileCount) {
}
