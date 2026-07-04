package org.camelia.studio.kiss.shot.acerola.services.recording;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class WavAudioFileWriter implements Closeable {
    private static final int SAMPLE_RATE = 48_000;
    private static final int CHANNELS = 2;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int HEADER_SIZE = 44;

    private final RandomAccessFile file;
    private long dataSize;
    private boolean closed;

    public WavAudioFileWriter(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        this.file = new RandomAccessFile(path.toFile(), "rw");
        this.file.setLength(0);
        writeHeader(0);
    }

    public synchronized void writeJdaPcm(byte[] bigEndianPcm) throws IOException {
        ensureOpen();
        if (bigEndianPcm.length % 2 != 0) {
            throw new IOException("Le flux PCM doit contenir des échantillons 16 bits complets");
        }

        byte[] littleEndian = new byte[bigEndianPcm.length];
        for (int i = 0; i < bigEndianPcm.length; i += 2) {
            littleEndian[i] = bigEndianPcm[i + 1];
            littleEndian[i + 1] = bigEndianPcm[i];
        }

        file.seek(HEADER_SIZE + dataSize);
        file.write(littleEndian);
        dataSize += littleEndian.length;
    }

    public synchronized long dataSize() {
        return dataSize;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        file.seek(0);
        writeHeader(dataSize);
        file.close();
        closed = true;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Le fichier WAV est déjà fermé");
        }
    }

    private void writeHeader(long dataLength) throws IOException {
        int byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;

        file.writeBytes("RIFF");
        writeLittleEndianInt(36 + dataLength);
        file.writeBytes("WAVE");
        file.writeBytes("fmt ");
        writeLittleEndianInt(16);
        writeLittleEndianShort(1);
        writeLittleEndianShort(CHANNELS);
        writeLittleEndianInt(SAMPLE_RATE);
        writeLittleEndianInt(byteRate);
        writeLittleEndianShort(blockAlign);
        writeLittleEndianShort(BITS_PER_SAMPLE);
        file.writeBytes("data");
        writeLittleEndianInt(dataLength);
    }

    private void writeLittleEndianInt(long value) throws IOException {
        file.write((int) (value & 0xFF));
        file.write((int) ((value >> 8) & 0xFF));
        file.write((int) ((value >> 16) & 0xFF));
        file.write((int) ((value >> 24) & 0xFF));
    }

    private void writeLittleEndianShort(int value) throws IOException {
        file.write(value & 0xFF);
        file.write((value >> 8) & 0xFF);
    }
}
