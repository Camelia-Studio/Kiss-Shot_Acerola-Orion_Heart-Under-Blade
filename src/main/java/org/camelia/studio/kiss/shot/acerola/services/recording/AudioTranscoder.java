package org.camelia.studio.kiss.shot.acerola.services.recording;

import java.io.IOException;
import java.nio.file.Path;

public interface AudioTranscoder {
    void transcodeWavToMp3(Path wavPath, Path mp3Path, int bitrateKbps) throws IOException;
}
