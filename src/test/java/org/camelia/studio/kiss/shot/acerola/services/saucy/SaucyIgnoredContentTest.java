package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaucyIgnoredContentTest {

    @Test
    void ignoresAngleBracketAndSpoilerLinks() {
        assertTrue(SaucyIgnoredContent.hasIgnoredLink("look <https://x.com/a/status/1>"));
        assertTrue(SaucyIgnoredContent.hasIgnoredLink("look ||https://x.com/a/status/1||"));
        assertFalse(SaucyIgnoredContent.hasIgnoredLink("look https://x.com/a/status/1"));
    }

    @Test
    void ignoresOnlyPairedUrlMasks() {
        assertTrue(SaucyIgnoredContent.hasIgnoredLink("look <https://example.com>"));
        assertTrue(SaucyIgnoredContent.hasIgnoredLink("look ||https://example.com||"));
        assertFalse(SaucyIgnoredContent.hasIgnoredLink("look <https://example.com||"));
        assertFalse(SaucyIgnoredContent.hasIgnoredLink("look ||https://example.com>"));
    }

    @Test
    void ignoresOnlyUrlMasksAndNotMentions() {
        assertFalse(SaucyIgnoredContent.hasIgnoredLink("hello <@123> and <#456>"));
        assertFalse(SaucyIgnoredContent.hasIgnoredLink("plain text ||spoiler without url||"));
    }
}
