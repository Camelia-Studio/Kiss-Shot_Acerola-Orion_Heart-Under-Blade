package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixivUgoiraRendererTest {
    @Test
    void extractsFramesWritesConcatRunsFfmpegAndReadsOutputWithoutRealFfmpeg() throws IOException {
        byte[] renderedBytes = new byte[]{9, 8, 7};
        FakeFfmpegRunner runner = new FakeFfmpegRunner(renderedBytes);
        PixivUgoiraRenderer renderer = new PixivUgoiraRenderer(runner);

        Optional<byte[]> result = renderer.render(zipBytes(
                        entry("000000.jpg", new byte[]{1}),
                        entry("000001.jpg", new byte[]{2}),
                        entry("../evil.jpg", new byte[]{3})
                ),
                metadata(),
                "mp4",
                1500
        );

        assertTrue(result.isPresent());
        assertArrayEquals(renderedBytes, result.orElseThrow());
        assertEquals("ffmpeg", runner.command.getFirst());
        assertEquals("-y", runner.command.get(1));
        assertEquals("-f", runner.command.get(2));
        assertEquals("concat", runner.command.get(3));
        assertEquals("-i", runner.command.get(4));
        assertEquals("-b:v", runner.command.get(6));
        assertEquals("1500k", runner.command.get(7));
        assertEquals("-pix_fmt", runner.command.get(8));
        assertEquals("yuv420p", runner.command.get(9));
        assertEquals("-filter:v", runner.command.get(10));
        assertEquals("pad=ceil(iw/2)*2:ceil(ih/2)*2", runner.command.get(11));
        assertEquals("""
                ffconcat version 1.0
                file 000000.jpg
                duration 0.06
                file 000001.jpg
                duration 0.12
                file 000001.jpg
                """, runner.concat);
        assertFalse(runner.siblingEscapeFileExisted);
        assertFalse(Files.exists(runner.tempDir));
    }

    @Test
    void returnsEmptyWhenFfmpegExitsNonZero() throws IOException {
        FakeFfmpegRunner runner = new FakeFfmpegRunner(new byte[]{9, 8, 7});
        runner.exitCode = 1;
        PixivUgoiraRenderer renderer = new PixivUgoiraRenderer(runner);

        Optional<byte[]> result = renderer.render(zipBytes(
                entry("000000.jpg", new byte[]{1}),
                entry("000001.jpg", new byte[]{2})
        ), metadata(), "mp4", 1500);

        assertTrue(result.isEmpty());
        assertFalse(Files.exists(runner.tempDir));
    }

    private static PixivUgoiraMetadata metadata() {
        return new PixivUgoiraMetadata(
                "https://i.pximg.net/img-zip-ugoira/106848609_ugoira1920x1080.zip",
                "https://i.pximg.net/img-zip-ugoira/106848609_ugoira600x600.zip",
                "image/jpeg",
                List.of(
                        new PixivUgoiraFrame("000000.jpg", 60),
                        new PixivUgoiraFrame("000001.jpg", 120)
                )
        );
    }

    private static ZipFixtureEntry entry(String name, byte[] bytes) {
        return new ZipFixtureEntry(name, bytes);
    }

    private static byte[] zipBytes(ZipFixtureEntry... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (ZipFixtureEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.bytes());
                zip.closeEntry();
            }
        }

        return bytes.toByteArray();
    }

    private record ZipFixtureEntry(String name, byte[] bytes) {
    }

    private static final class FakeFfmpegRunner implements PixivUgoiraFfmpegRunner {
        private final byte[] outputBytes;
        private List<String> command;
        private String concat;
        private Path tempDir;
        private boolean siblingEscapeFileExisted;
        private int exitCode;

        private FakeFfmpegRunner(byte[] outputBytes) {
            this.outputBytes = outputBytes;
        }

        @Override
        public int run(List<String> command) throws IOException {
            this.command = List.copyOf(command);
            Path concatPath = Path.of(command.get(5));
            tempDir = concatPath.getParent();
            concat = Files.readString(concatPath);
            Path siblingEscapeFile = tempDir.getParent().resolve("evil.jpg");
            siblingEscapeFileExisted = Files.exists(siblingEscapeFile);
            Files.deleteIfExists(siblingEscapeFile);
            Files.write(Path.of(command.getLast()), outputBytes);
            return exitCode;
        }
    }
}
