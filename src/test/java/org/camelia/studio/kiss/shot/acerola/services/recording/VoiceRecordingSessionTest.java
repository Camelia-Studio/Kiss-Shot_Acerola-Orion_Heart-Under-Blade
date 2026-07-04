package org.camelia.studio.kiss.shot.acerola.services.recording;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceRecordingSessionTest {
    @TempDir
    Path tempDir;

    @Test
    void tracksModeKeepsUsingTheSameFileForAUserAcrossReconnects() throws IOException {
        VoiceRecordingSession session = new VoiceRecordingSession(
                "guild-1",
                "Salon vocal",
                RecordingMode.TRACKS,
                tempDir.resolve("session"),
                Instant.parse("2026-07-01T12:00:00Z"));

        session.recordUserAudio("42", "Alice / Main", new byte[]{0x01, 0x02});
        session.recordUserAudio("42", "Alice / Main", new byte[]{0x03, 0x04});

        RecordingStopResult result = session.stop(new CopyingTranscoder());

        assertEquals(1, result.files().size());
        assertEquals("recording-20260701-120000-salon-vocal-alice-main-42.mp3", result.files().getFirst().fileName());
        assertArrayEquals(new byte[]{0x02, 0x01, 0x04, 0x03}, data(Files.readAllBytes(result.files().getFirst().path())));
    }

    @Test
    void mixModeMixesQueuedBotMusicIntoTheCombinedVoiceTrack() throws IOException {
        VoiceRecordingSession session = new VoiceRecordingSession(
                "guild-1",
                "Salon vocal",
                RecordingMode.MIX,
                tempDir.resolve("session"),
                Instant.parse("2026-07-01T12:00:00Z"));

        session.recordBotAudio(new byte[]{0x00, 0x64});
        session.recordCombinedAudio(new byte[]{0x00, 0x64});

        RecordingStopResult result = session.stop(new CopyingTranscoder());

        assertEquals(1, result.files().size());
        assertEquals("recording-20260701-120000-salon-vocal-mix.mp3", result.files().getFirst().fileName());
        assertArrayEquals(new byte[]{(byte) 0xC8, 0x00}, data(Files.readAllBytes(result.files().getFirst().path())));
    }

    @Test
    void tracksModePadsQuietTracksOnTheSharedTimeline() throws IOException {
        MutableNanoClock clock = new MutableNanoClock();
        VoiceRecordingSession session = new VoiceRecordingSession(
                "guild-1",
                "Salon vocal",
                RecordingMode.TRACKS,
                tempDir.resolve("session"),
                Instant.parse("2026-07-01T12:00:00Z"),
                96,
                clock::nanos);

        session.recordUserAudio("1", "Alice", new byte[]{0x01, 0x02});
        clock.advanceFrame();
        session.recordUserAudio("2", "Bob", new byte[]{0x03, 0x04});
        clock.advanceFrame();

        RecordingStopResult result = session.stop(new CopyingTranscoder());

        assertEquals(2, result.files().size());
        assertArrayEquals(new byte[]{0x02, 0x01, 0x00, 0x00}, dataFor(result, "alice-1"));
        assertArrayEquals(new byte[]{0x00, 0x00, 0x04, 0x03}, dataFor(result, "bob-2"));
    }

    @Test
    void tracksModeStoresBotMusicInADedicatedTrack() throws IOException {
        VoiceRecordingSession session = new VoiceRecordingSession(
                "guild-1",
                "Salon vocal",
                RecordingMode.TRACKS,
                tempDir.resolve("session"),
                Instant.parse("2026-07-01T12:00:00Z"));

        session.recordBotAudio(new byte[]{0x01, 0x02});

        RecordingStopResult result = session.stop(new CopyingTranscoder());

        assertEquals(1, result.files().size());
        assertEquals("recording-20260701-120000-salon-vocal-musique-bot.mp3", result.files().getFirst().fileName());
        assertArrayEquals(new byte[]{0x02, 0x01}, data(Files.readAllBytes(result.files().getFirst().path())));
    }

    private static byte[] data(byte[] bytes) {
        byte[] data = new byte[bytes.length - 44];
        System.arraycopy(bytes, 44, data, 0, data.length);
        return data;
    }

    private static byte[] dataFor(RecordingStopResult result, String fileNamePart) throws IOException {
        RecordingArtifact file = result.files().stream()
                .filter(candidate -> candidate.fileName().contains(fileNamePart))
                .findFirst()
                .orElseThrow();
        return data(Files.readAllBytes(file.path()));
    }

    private static final class MutableNanoClock {
        private long nanos;

        private long nanos() {
            return nanos;
        }

        private void advanceFrame() {
            nanos += 20_000_000L;
        }
    }

    private static final class CopyingTranscoder implements AudioTranscoder {
        private final List<Path> sources = new ArrayList<>();

        @Override
        public void transcodeWavToMp3(Path wavPath, Path mp3Path, int bitrateKbps) throws IOException {
            sources.add(wavPath);
            Files.copy(wavPath, mp3Path);
        }
    }
}
