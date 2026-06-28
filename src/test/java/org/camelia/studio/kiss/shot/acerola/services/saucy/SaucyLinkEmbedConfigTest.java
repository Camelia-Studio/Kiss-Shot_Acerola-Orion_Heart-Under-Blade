package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaucyLinkEmbedConfigTest {

    @Test
    void defaultsMatchSaucyBehavior() {
        SaucyLinkEmbedConfig config = SaucyLinkEmbedConfig.from(Map.of());

        assertTrue(config.enabled());
        assertEquals(3600, config.cacheTtlSeconds());
        assertEquals(8, config.maxLinksPerMessage());
        assertEquals(4, config.maxEmbedsPerMessage());
        assertEquals(10_485_760L, config.maxFileBytes());
        assertTrue(config.sendMatchedMessage());
        assertEquals("Traitement du lien en cours...", config.matchedMessage());
        assertEquals(5, config.pixivImageLimit());
        assertEquals("mp4", config.pixivUgoiraFormat());
        assertEquals(2000, config.pixivUgoiraBitrate());
        assertEquals("misskey.io", config.misskeyDomains().get(0));
        assertEquals("misskey.design", config.misskeyDomains().get(1));
        assertEquals("oekakiskey.com", config.misskeyDomains().get(2));
    }

    @Test
    void invalidNumbersFallbackToDefaults() {
        SaucyLinkEmbedConfig config = SaucyLinkEmbedConfig.from(Map.of(
                "SAUCY_MAX_LINKS_PER_MESSAGE", "0",
                "SAUCY_MAX_FILE_BYTES", "-1",
                "SAUCY_PIXIV_IMAGE_LIMIT", "abc"
        ));

        assertEquals(8, config.maxLinksPerMessage());
        assertEquals(10_485_760L, config.maxFileBytes());
        assertEquals(5, config.pixivImageLimit());
    }

    @Test
    void parsesBooleansAndMisskeyDomains() {
        SaucyLinkEmbedConfig config = SaucyLinkEmbedConfig.from(Map.of(
                "SAUCY_LINK_EMBEDS_ENABLED", "false",
                "SAUCY_SEND_MATCHED_MESSAGE", "false",
                "SAUCY_MISSKEY_DOMAINS", "misskey.io, example.social ,"
        ));

        assertFalse(config.enabled());
        assertFalse(config.sendMatchedMessage());
        assertEquals(2, config.misskeyDomains().size());
        assertEquals("example.social", config.misskeyDomains().get(1));
    }
}
