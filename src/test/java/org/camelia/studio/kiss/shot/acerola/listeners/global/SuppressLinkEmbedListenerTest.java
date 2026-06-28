package org.camelia.studio.kiss.shot.acerola.listeners.global;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuppressLinkEmbedListenerTest {

    @Test
    void skipsBotMessagesSoSaucyRepliesKeepTheirEmbeds() {
        assertTrue(SuppressLinkEmbedListener.shouldSkipSuppression(true, false, false, false));
    }

    @Test
    void skipsSaucySupportedLinksWhenSaucyIsEnabled() {
        assertTrue(SuppressLinkEmbedListener.shouldSkipSuppression(false, true, false, true));
    }

    @Test
    void doesNotSkipNonSaucyUserMessages() {
        assertFalse(SuppressLinkEmbedListener.shouldSkipSuppression(false, true, false, false));
    }

    @Test
    void doesNotSkipIgnoredSaucyLinks() {
        assertFalse(SuppressLinkEmbedListener.shouldSkipSuppression(false, true, true, true));
    }
}
