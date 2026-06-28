package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaucyNsfwGuardTest {

    private final SaucyNsfwGuard guard = new SaucyNsfwGuard();

    @Test
    void allowsNonSensitiveContentInNonNsfwChannel() {
        assertTrue(guard.canPost(false, false));
    }

    @Test
    void allowsNonSensitiveContentInNsfwChannel() {
        assertTrue(guard.canPost(false, true));
    }

    @Test
    void blocksSensitiveContentInNonNsfwChannel() {
        assertFalse(guard.canPost(true, false));
    }

    @Test
    void allowsSensitiveContentInNsfwChannel() {
        assertTrue(guard.canPost(true, true));
    }
}
