package org.camelia.studio.kiss.shot.acerola.services.recording;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WavAudioFileWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesStandardWavHeaderAndConvertsJdaBigEndianPcmToLittleEndian() throws IOException {
        Path output = tempDir.resolve("voice.wav");

        try (WavAudioFileWriter writer = new WavAudioFileWriter(output)) {
            writer.writeJdaPcm(new byte[]{0x01, 0x02, 0x03, 0x04});
        }

        byte[] bytes = Files.readAllBytes(output);

        assertEquals("RIFF", ascii(bytes, 0, 4));
        assertEquals("WAVE", ascii(bytes, 8, 4));
        assertEquals("fmt ", ascii(bytes, 12, 4));
        assertEquals("data", ascii(bytes, 36, 4));
        assertEquals(36 + 4, littleEndianInt(bytes, 4));
        assertEquals(16, littleEndianInt(bytes, 16));
        assertEquals(1, littleEndianShort(bytes, 20));
        assertEquals(2, littleEndianShort(bytes, 22));
        assertEquals(48_000, littleEndianInt(bytes, 24));
        assertEquals(192_000, littleEndianInt(bytes, 28));
        assertEquals(4, littleEndianShort(bytes, 32));
        assertEquals(16, littleEndianShort(bytes, 34));
        assertEquals(4, littleEndianInt(bytes, 40));
        assertArrayEquals(new byte[]{0x02, 0x01, 0x04, 0x03}, data(bytes));
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static byte[] data(byte[] bytes) {
        byte[] data = new byte[bytes.length - 44];
        System.arraycopy(bytes, 44, data, 0, data.length);
        return data;
    }
}
