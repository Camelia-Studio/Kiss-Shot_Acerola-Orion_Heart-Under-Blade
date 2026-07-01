package org.camelia.studio.kiss.shot.acerola.services.recording;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class FfmpegAudioTranscoder implements AudioTranscoder {
    private final String ffmpegCommand;

    public FfmpegAudioTranscoder(String ffmpegCommand) {
        this.ffmpegCommand = ffmpegCommand;
    }

    @Override
    public void transcodeWavToMp3(Path wavPath, Path mp3Path, int bitrateKbps) throws IOException {
        Process process = new ProcessBuilder(
                ffmpegCommand,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                wavPath.toString(),
                "-codec:a",
                "libmp3lame",
                "-b:a",
                bitrateKbps + "k",
                mp3Path.toString())
                .redirectErrorStream(true)
                .start();

        try {
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("ffmpeg n'a pas terminé l'encodage MP3 dans le délai imparti");
            }
            if (process.exitValue() != 0) {
                throw new IOException("ffmpeg a échoué pendant l'encodage MP3: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Encodage MP3 interrompu", e);
        }
    }
}
