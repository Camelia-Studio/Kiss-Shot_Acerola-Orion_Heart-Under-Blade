package org.camelia.studio.kiss.shot.acerola.services.recording;

final class PcmAudioMixer {
    private PcmAudioMixer() {
    }

    static byte[] mixBigEndianPcm(byte[] first, byte[] second) {
        int length = Math.max(first.length, second.length);
        if (length % 2 != 0) {
            length++;
        }

        byte[] mixed = new byte[length];
        for (int i = 0; i < length; i += 2) {
            int sample = readSample(first, i) + readSample(second, i);
            sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
            mixed[i] = (byte) ((sample >> 8) & 0xFF);
            mixed[i + 1] = (byte) (sample & 0xFF);
        }

        return mixed;
    }

    private static int readSample(byte[] bytes, int offset) {
        if (offset + 1 >= bytes.length) {
            return 0;
        }
        return (short) (((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF));
    }
}
