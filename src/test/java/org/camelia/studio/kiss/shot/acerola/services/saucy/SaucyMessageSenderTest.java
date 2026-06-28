package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaucyMessageSenderTest {

    @Test
    void prepareOutboundMessagesSkipsSensitiveResponsesOutsideNsfwChannels() {
        SaucyMessageSender sender = sender();
        SaucyProcessResponse publicResponse = response("public", false);
        SaucyProcessResponse sensitiveResponse = response("sensitive", true);

        List<SaucyOutboundMessage> messages = sender.prepareOutboundMessages(
                List.of(publicResponse, sensitiveResponse),
                false
        );

        assertEquals(1, messages.size());
        assertEquals("public", messages.getFirst().text());
    }

    @Test
    void prepareOutboundMessagesIncludesSensitiveResponsesInsideNsfwChannels() {
        SaucyMessageSender sender = sender();
        SaucyProcessResponse publicResponse = response("public", false);
        SaucyProcessResponse sensitiveResponse = response("sensitive", true);

        List<SaucyOutboundMessage> messages = sender.prepareOutboundMessages(
                List.of(publicResponse, sensitiveResponse),
                true
        );

        assertEquals(2, messages.size());
        assertEquals("public", messages.getFirst().text());
        assertEquals("sensitive", messages.get(1).text());
    }

    private static SaucyMessageSender sender() {
        return new SaucyMessageSender(config(), new SaucyMessagePartitioner(4, 100), new SaucyNsfwGuard());
    }

    private static SaucyProcessResponse response(String text, boolean sensitive) {
        return new SaucyProcessResponse(text, List.of(), List.of(), sensitive);
    }

    private static SaucyLinkEmbedConfig config() {
        return new SaucyLinkEmbedConfig(
                true,
                3600,
                8,
                4,
                100,
                false,
                "",
                "",
                5,
                "mp4",
                2000,
                List.of("misskey.io")
        );
    }
}
