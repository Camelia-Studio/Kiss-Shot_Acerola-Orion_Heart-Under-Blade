package org.camelia.studio.kiss.shot.acerola.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JnaNativeCompatibilityTest {
    @Test
    void runtimeClasspathContainsAppleSiliconJnaNativeLibrary() {
        assertNotNull(
                getClass().getClassLoader().getResource("com/sun/jna/darwin-aarch64/libjnidispatch.jnilib"),
                "JNA must include a darwin-aarch64 native library for Apple Silicon local runs");
    }
}
