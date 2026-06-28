package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

interface PixivUgoiraRendererGateway {
    Optional<byte[]> render(byte[] zipBytes, PixivUgoiraMetadata metadata, String format, int bitrate);
}

interface PixivUgoiraFfmpegRunner {
    int run(List<String> command) throws IOException, InterruptedException;
}

public class PixivUgoiraRenderer implements PixivUgoiraRendererGateway {
    private final PixivUgoiraFfmpegRunner ffmpegRunner;

    public PixivUgoiraRenderer() {
        this(new ProcessBuilderFfmpegRunner());
    }

    PixivUgoiraRenderer(PixivUgoiraFfmpegRunner ffmpegRunner) {
        this.ffmpegRunner = ffmpegRunner;
    }

    @Override
    public Optional<byte[]> render(byte[] zipBytes, PixivUgoiraMetadata metadata, String format, int bitrate) {
        if (zipBytes == null || zipBytes.length == 0 || metadata == null || metadata.frames() == null) {
            return Optional.empty();
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kiss-shot-pixiv-ugoira-");
            extractZip(zipBytes, tempDir);

            List<FrameFile> frames = frameFiles(tempDir, metadata.frames());
            if (frames.isEmpty()) {
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

            int exitCode = ffmpegRunner.run(command);
            if (exitCode != 0 || !Files.isRegularFile(output)) {
                return Optional.empty();
            }

            return Optional.of(Files.readAllBytes(output));
        } catch (IOException ignored) {
            return Optional.empty();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void extractZip(byte[] zipBytes, Path tempDir) throws IOException {
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

                    Files.createDirectories(output.get().getParent());
                    Files.copy(zip, output.get(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    zip.closeEntry();
                }
            }
        }
    }

    private static List<FrameFile> frameFiles(Path tempDir, List<PixivUgoiraFrame> frames) {
        List<FrameFile> files = new ArrayList<>();
        for (PixivUgoiraFrame frame : frames) {
            if (frame == null || frame.delay() <= 0) {
                return List.of();
            }

            Optional<Path> path = safePath(tempDir, frame.file());
            if (path.isEmpty() || !Files.isRegularFile(path.get())) {
                return List.of();
            }

            String relativePath = tempDir.relativize(path.get()).toString().replace('\\', '/');
            if (!relativePath.matches("[A-Za-z0-9._/-]+")) {
                return List.of();
            }

            files.add(new FrameFile(relativePath, frame.delay()));
        }

        return files;
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

    private record FrameFile(String file, int delayMillis) {
    }

    private static final class ProcessBuilderFfmpegRunner implements PixivUgoiraFfmpegRunner {
        @Override
        public int run(List<String> command) throws IOException, InterruptedException {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor();
        }
    }
}
