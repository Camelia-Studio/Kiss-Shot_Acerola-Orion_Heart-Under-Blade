package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

interface PixivUgoiraRendererGateway {
    Optional<byte[]> render(byte[] zipBytes, PixivUgoiraMetadata metadata, String format, int bitrate, long maxBytes);
}

interface PixivUgoiraFfmpegRunner {
    int run(List<String> command, Duration timeout) throws IOException, InterruptedException;
}

public class PixivUgoiraRenderer implements PixivUgoiraRendererGateway {
    private static final Duration FFMPEG_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_FRAMES = 500;
    private static final long MAX_TOTAL_DURATION_MILLIS = 120_000L;
    private static final int MAX_FRAME_DELAY_MILLIS = 10_000;

    private final PixivUgoiraFfmpegRunner ffmpegRunner;

    public PixivUgoiraRenderer() {
        this(new ProcessBuilderFfmpegRunner());
    }

    PixivUgoiraRenderer(PixivUgoiraFfmpegRunner ffmpegRunner) {
        this.ffmpegRunner = ffmpegRunner;
    }

    @Override
    public Optional<byte[]> render(
            byte[] zipBytes,
            PixivUgoiraMetadata metadata,
            String format,
            int bitrate,
            long maxBytes
    ) {
        if (maxBytes <= 0 || zipBytes == null || zipBytes.length == 0 || metadata == null || metadata.frames() == null) {
            return Optional.empty();
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kiss-shot-pixiv-ugoira-");
            List<FrameFile> frames = frameFiles(tempDir, metadata.frames());
            if (frames.isEmpty()) {
                return Optional.empty();
            }
            extractZip(zipBytes, tempDir, frames, maxBytes);
            if (!allFramesExtracted(frames)) {
                return Optional.empty();
            }

            Path concat = writeConcat(tempDir, frames);
            String outputFormat = safeFormat(format);
            Path output = tempDir.resolve("output." + outputFormat);
            List<String> command = List.of(
                    "ffmpeg",
                    "-y",
                    "-f",
                    "concat",
                    "-i",
                    concat.toString(),
                    "-b:v",
                    Math.max(1, bitrate) + "k",
                    "-pix_fmt",
                    "yuv420p",
                    "-filter:v",
                    "pad=ceil(iw/2)*2:ceil(ih/2)*2",
                    output.toString()
            );

            int exitCode = ffmpegRunner.run(command, FFMPEG_TIMEOUT);
            if (exitCode != 0 || !Files.isRegularFile(output)) {
                return Optional.empty();
            }

            return readCapped(output, maxBytes);
        } catch (IOException ignored) {
            return Optional.empty();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void extractZip(byte[] zipBytes, Path tempDir, List<FrameFile> frames, long maxBytes) throws IOException {
        Set<Path> allowedPaths = new HashSet<>();
        for (FrameFile frame : frames) {
            allowedPaths.add(frame.path());
        }

        long totalBytes = 0L;
        int extractedEntries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    Optional<Path> output = safePath(tempDir, entry.getName());
                    if (output.isEmpty()) {
                        continue;
                    }
                    if (!allowedPaths.contains(output.get())) {
                        continue;
                    }
                    extractedEntries++;
                    if (extractedEntries > allowedPaths.size()) {
                        throw new IOException("Too many ugoira frame entries");
                    }

                    Files.createDirectories(output.get().getParent());
                    totalBytes += copyCapped(zip, output.get(), maxBytes, maxBytes - totalBytes);
                } finally {
                    zip.closeEntry();
                }
            }
        }
    }

    private static List<FrameFile> frameFiles(Path tempDir, List<PixivUgoiraFrame> frames) {
        if (frames.isEmpty() || frames.size() > MAX_FRAMES) {
            return List.of();
        }

        List<FrameFile> files = new ArrayList<>();
        long totalDuration = 0L;
        for (PixivUgoiraFrame frame : frames) {
            if (frame == null || frame.delay() <= 0 || frame.delay() > MAX_FRAME_DELAY_MILLIS) {
                return List.of();
            }
            totalDuration += frame.delay();
            if (totalDuration > MAX_TOTAL_DURATION_MILLIS) {
                return List.of();
            }

            Optional<Path> path = safePath(tempDir, frame.file());
            if (path.isEmpty()) {
                return List.of();
            }

            String relativePath = tempDir.relativize(path.get()).toString().replace('\\', '/');
            if (!relativePath.matches("[A-Za-z0-9._/-]+")) {
                return List.of();
            }

            files.add(new FrameFile(relativePath, path.get(), frame.delay()));
        }

        return files;
    }

    private static boolean allFramesExtracted(List<FrameFile> frames) {
        return frames.stream().allMatch(frame -> Files.isRegularFile(frame.path()));
    }

    private static long copyCapped(InputStream input, Path output, long entryLimit, long totalRemaining) throws IOException {
        if (entryLimit <= 0 || totalRemaining <= 0) {
            throw new IOException("Ugoira extraction limit exceeded");
        }

        byte[] buffer = new byte[8192];
        long written = 0L;
        try (OutputStream outputStream = Files.newOutputStream(output)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                written += read;
                if (written > entryLimit || written > totalRemaining) {
                    throw new IOException("Ugoira extraction limit exceeded");
                }

                outputStream.write(buffer, 0, read);
            }
        }

        return written;
    }

    private static Path writeConcat(Path tempDir, List<FrameFile> frames) throws IOException {
        StringBuilder concat = new StringBuilder("ffconcat version 1.0\n");
        for (FrameFile frame : frames) {
            concat.append("file ").append(frame.file()).append('\n');
            concat.append("duration ").append(delaySeconds(frame.delayMillis())).append('\n');
        }

        concat.append("file ").append(frames.getLast().file()).append('\n');
        Path concatPath = tempDir.resolve("frames.ffconcat");
        Files.writeString(concatPath, concat.toString(), StandardCharsets.UTF_8);
        return concatPath;
    }

    private static String delaySeconds(int delayMillis) {
        return BigDecimal.valueOf(delayMillis, 3).stripTrailingZeros().toPlainString();
    }

    private static Optional<byte[]> readCapped(Path path, long maxBytes) throws IOException {
        if (maxBytes <= 0 || Files.size(path) > maxBytes) {
            return Optional.empty();
        }

        try (InputStream stream = Files.newInputStream(path)) {
            return readCapped(stream, maxBytes);
        }
    }

    private static Optional<byte[]> readCapped(InputStream stream, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = stream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return Optional.empty();
            }

            output.write(buffer, 0, read);
        }

        return Optional.of(output.toByteArray());
    }

    private static Optional<Path> safePath(Path tempDir, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        Path path = tempDir.resolve(name.replace('\\', '/')).normalize();
        if (!path.startsWith(tempDir)) {
            return Optional.empty();
        }

        return Optional.of(path);
    }

    private static String safeFormat(String format) {
        if (format == null) {
            return "mp4";
        }

        String safe = format.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return safe.isBlank() ? "mp4" : safe;
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record FrameFile(String file, Path path, int delayMillis) {
    }

    private static final class ProcessBuilderFfmpegRunner implements PixivUgoiraFfmpegRunner {
        @Override
        public int run(List<String> command, Duration timeout) throws IOException, InterruptedException {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor();
                    return -1;
                }

                return process.exitValue();
            } catch (InterruptedException interruptedException) {
                process.destroyForcibly();
                throw interruptedException;
            }
        }
    }
}
