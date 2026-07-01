package org.camelia.studio.kiss.shot.acerola.utils;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpusNativeLibraryLoaderTest {
    @Test
    void appleSiliconCandidatesPreferConfiguredPathThenHomebrewArmPaths() {
        List<Path> candidates = OpusNativeLibraryLoader.candidatePaths(
                "Mac OS X",
                "aarch64",
                "/custom/libopus.dylib");

        assertEquals(Path.of("/custom/libopus.dylib"), candidates.getFirst());
        assertTrue(candidates.contains(Path.of("/opt/homebrew/lib/libopus.dylib")));
        assertTrue(candidates.contains(Path.of("/opt/homebrew/opt/opus/lib/libopus.dylib")));
    }

    @Test
    void nonAppleSiliconOnlyUsesConfiguredPath() {
        List<Path> candidates = OpusNativeLibraryLoader.candidatePaths(
                "Linux",
                "amd64",
                "/custom/libopus.so");

        assertEquals(List.of(Path.of("/custom/libopus.so")), candidates);
    }
}
