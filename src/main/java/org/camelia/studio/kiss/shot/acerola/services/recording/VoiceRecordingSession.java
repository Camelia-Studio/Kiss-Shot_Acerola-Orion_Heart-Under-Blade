package org.camelia.studio.kiss.shot.acerola.services.recording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.function.LongSupplier;

public class VoiceRecordingSession {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);
    private static final int DEFAULT_MP3_BITRATE_KBPS = 96;
    private static final long TRACK_FRAME_NANOS = 20_000_000L;
    private static final int DEFAULT_TRACK_FRAME_BYTES = 48_000 * 2 * 2 / 50;

    private final String guildId;
    private final String channelName;
    private final RecordingMode mode;
    private final Path sessionDirectory;
    private final Instant startedAt;
    private final int mp3BitrateKbps;
    private final LongSupplier nanoTime;
    private final long timelineStartedAtNanos;
    private final Map<String, TrackWriter> trackWriters = new LinkedHashMap<>();
    private final Queue<byte[]> queuedBotFrames = new ArrayDeque<>();

    private TrackWriter mixWriter;
    private int trackFrameBytes = DEFAULT_TRACK_FRAME_BYTES;
    private boolean stopped;

    public VoiceRecordingSession(
            String guildId,
            String channelName,
            RecordingMode mode,
            Path sessionDirectory,
            Instant startedAt) {
        this(guildId, channelName, mode, sessionDirectory, startedAt, DEFAULT_MP3_BITRATE_KBPS);
    }

    public VoiceRecordingSession(
            String guildId,
            String channelName,
            RecordingMode mode,
            Path sessionDirectory,
            Instant startedAt,
            int mp3BitrateKbps) {
        this(guildId, channelName, mode, sessionDirectory, startedAt, mp3BitrateKbps, System::nanoTime);
    }

    VoiceRecordingSession(
            String guildId,
            String channelName,
            RecordingMode mode,
            Path sessionDirectory,
            Instant startedAt,
            int mp3BitrateKbps,
            LongSupplier nanoTime) {
        this.guildId = guildId;
        this.channelName = channelName;
        this.mode = mode;
        this.sessionDirectory = sessionDirectory;
        this.startedAt = startedAt;
        this.mp3BitrateKbps = mp3BitrateKbps;
        this.nanoTime = nanoTime;
        this.timelineStartedAtNanos = nanoTime.getAsLong();
    }

    public synchronized void recordCombinedAudio(byte[] combinedPcm) throws IOException {
        if (stopped) {
            return;
        }

        if (mode != RecordingMode.MIX) {
            return;
        }

        byte[] botPcm = queuedBotFrames.poll();
        byte[] output = botPcm == null ? combinedPcm : PcmAudioMixer.mixBigEndianPcm(combinedPcm, botPcm);
        getMixWriter().writer().writeJdaPcm(output);
    }

    public synchronized void recordUserAudio(String userId, String userName, byte[] userPcm) throws IOException {
        if (mode != RecordingMode.TRACKS || stopped) {
            return;
        }

        rememberTrackFrameSize(userPcm);
        TrackWriter writer = getTrackWriter(userId, userName);
        padTrackWriterTo(writer, currentTrackFrameIndex());
        writeTrackFrame(writer, userPcm);
    }

    public synchronized void recordBotAudio(byte[] botPcm) throws IOException {
        if (stopped) {
            return;
        }

        if (mode == RecordingMode.TRACKS) {
            rememberTrackFrameSize(botPcm);
            TrackWriter writer = getTrackWriter("bot-music", "Musique bot");
            padTrackWriterTo(writer, currentTrackFrameIndex());
            writeTrackFrame(writer, botPcm);
            return;
        }

        queuedBotFrames.offer(botPcm.clone());
    }

    public synchronized RecordingStopResult stop(AudioTranscoder transcoder) throws IOException {
        if (stopped) {
            return new RecordingStopResult(guildId, channelName, mode, startedAt, Instant.now(), List.of());
        }

        if (mode == RecordingMode.TRACKS) {
            padTrackWritersToStopFrame();
        } else if (mode == RecordingMode.MIX && mixWriter != null) {
            while (!queuedBotFrames.isEmpty()) {
                mixWriter.writer().writeJdaPcm(queuedBotFrames.poll());
            }
        }
        stopped = true;

        List<TrackWriter> writers = new ArrayList<>();
        if (mixWriter != null) {
            writers.add(mixWriter);
        }
        writers.addAll(trackWriters.values());

        List<RecordingArtifact> artifacts = new ArrayList<>();
        for (TrackWriter writer : writers) {
            writer.writer().close();
            if (writer.writer().dataSize() == 0) {
                Files.deleteIfExists(writer.wavPath());
                continue;
            }

            Path mp3Path = replaceExtension(writer.wavPath(), ".mp3");
            transcoder.transcodeWavToMp3(writer.wavPath(), mp3Path, mp3BitrateKbps);
            Files.deleteIfExists(writer.wavPath());
            artifacts.add(new RecordingArtifact(replaceExtension(writer.fileName(), ".mp3"), mp3Path));
        }

        return new RecordingStopResult(guildId, channelName, mode, startedAt, Instant.now(), List.copyOf(artifacts));
    }

    public synchronized int openFileCount() {
        int count = trackWriters.size();
        return mixWriter == null ? count : count + 1;
    }

    public RecordingMode mode() {
        return mode;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public String channelName() {
        return channelName;
    }

    private TrackWriter getMixWriter() throws IOException {
        if (mixWriter == null) {
            mixWriter = createWriter("mix");
        }
        return mixWriter;
    }

    private TrackWriter getTrackWriter(String userId, String userName) throws IOException {
        TrackWriter writer = trackWriters.get(userId);
        if (writer != null) {
            return writer;
        }

        String label = "bot-music".equals(userId) ? "musique-bot" : userName + "-" + userId;
        writer = createWriter(label);
        trackWriters.put(userId, writer);
        return writer;
    }

    private void rememberTrackFrameSize(byte[] pcm) {
        if (pcm.length > 0 && pcm.length % 2 == 0) {
            trackFrameBytes = pcm.length;
        }
    }

    private long currentTrackFrameIndex() {
        long elapsedNanos = Math.max(0, nanoTime.getAsLong() - timelineStartedAtNanos);
        return elapsedNanos / TRACK_FRAME_NANOS;
    }

    private long currentTrackFrameCount() {
        long elapsedNanos = Math.max(0, nanoTime.getAsLong() - timelineStartedAtNanos);
        return (elapsedNanos + TRACK_FRAME_NANOS - 1) / TRACK_FRAME_NANOS;
    }

    private void padTrackWritersToStopFrame() throws IOException {
        long targetFrameCount = currentTrackFrameCount();
        for (TrackWriter writer : trackWriters.values()) {
            targetFrameCount = Math.max(targetFrameCount, writer.framesWritten());
        }

        for (TrackWriter writer : trackWriters.values()) {
            padTrackWriterTo(writer, targetFrameCount);
        }
    }

    private void padTrackWriterTo(TrackWriter writer, long targetFrameCount) throws IOException {
        byte[] silenceFrame = null;
        while (writer.framesWritten() < targetFrameCount) {
            if (silenceFrame == null) {
                silenceFrame = silenceFrame();
            }
            writeTrackFrame(writer, silenceFrame);
        }
    }

    private void writeTrackFrame(TrackWriter writer, byte[] pcm) throws IOException {
        writer.writer().writeJdaPcm(pcm);
        writer.markFrameWritten();
    }

    private byte[] silenceFrame() {
        return new byte[trackFrameBytes];
    }

    private TrackWriter createWriter(String label) throws IOException {
        Files.createDirectories(sessionDirectory);
        String fileName = "recording-%s-%s-%s.wav".formatted(
                FILE_DATE_FORMAT.format(startedAt),
                slug(channelName),
                slug(label));
        Path wavPath = sessionDirectory.resolve(fileName);
        return new TrackWriter(fileName, wavPath, new WavAudioFileWriter(wavPath));
    }

    private static String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "audio" : normalized;
    }

    private static Path replaceExtension(Path path, String extension) {
        return path.resolveSibling(replaceExtension(path.getFileName().toString(), extension));
    }

    private static String replaceExtension(String fileName, String extension) {
        int dot = fileName.lastIndexOf('.');
        String base = dot == -1 ? fileName : fileName.substring(0, dot);
        return base + extension;
    }

    private static final class TrackWriter {
        private final String fileName;
        private final Path wavPath;
        private final WavAudioFileWriter writer;
        private long framesWritten;

        private TrackWriter(String fileName, Path wavPath, WavAudioFileWriter writer) {
            this.fileName = fileName;
            this.wavPath = wavPath;
            this.writer = writer;
        }

        private String fileName() {
            return fileName;
        }

        private Path wavPath() {
            return wavPath;
        }

        private WavAudioFileWriter writer() {
            return writer;
        }

        private long framesWritten() {
            return framesWritten;
        }

        private void markFrameWritten() {
            framesWritten++;
        }
    }

}
