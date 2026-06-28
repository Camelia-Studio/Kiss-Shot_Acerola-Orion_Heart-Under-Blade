package org.camelia.studio.kiss.shot.acerola.listeners.global;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaucyLinkEmbedListenerTest {
    @Test
    void ignoreReasonExplainsWhyMessageWillNotBeProcessed() {
        assertEquals(
                Optional.of("saucy link embeds are disabled"),
                SaucyLinkEmbedListener.ignoreReason(false, true, false, "https://x.com/alice/status/1")
        );
        assertEquals(
                Optional.of("message is outside a guild"),
                SaucyLinkEmbedListener.ignoreReason(true, false, false, "https://x.com/alice/status/1")
        );
        assertEquals(
                Optional.of("author is a bot"),
                SaucyLinkEmbedListener.ignoreReason(true, true, true, "https://x.com/alice/status/1")
        );
        assertEquals(
                Optional.of("message content is blank"),
                SaucyLinkEmbedListener.ignoreReason(true, true, false, " ")
        );
        assertEquals(
                Optional.of("link is explicitly ignored"),
                SaucyLinkEmbedListener.ignoreReason(true, true, false, "<https://x.com/alice/status/1>")
        );
    }
}
