package org.camelia.studio.kiss.shot.acerola.services.saucy;

import net.dv8tion.jda.api.entities.channel.attribute.IAgeRestrictedChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void suppressesNativeEmbedsOnlyWhenEveryFinalMessageSucceeded() {
        assertTrue(SaucyMessageSender.shouldSuppressSourceEmbeds(true, false));
        assertFalse(SaucyMessageSender.shouldSuppressSourceEmbeds(true, true));
        assertFalse(SaucyMessageSender.shouldSuppressSourceEmbeds(false, false));
    }

    @Test
    void detectsNsfwParentForThreadChannels() {
        IThreadContainerUnion parent = proxy(
                new Class<?>[]{IThreadContainerUnion.class, IAgeRestrictedChannel.class},
                "isNSFW",
                true
        );
        ThreadChannel thread = proxy(new Class<?>[]{ThreadChannel.class}, "getParentChannel", parent);

        assertTrue(SaucyMessageSender.isNsfwChannel(thread));
    }

    @Test
    void detectsDirectNsfwChannels() {
        GuildChannel channel = proxy(
                new Class<?>[]{GuildChannel.class, IAgeRestrictedChannel.class},
                "isNSFW",
                true
        );

        assertTrue(SaucyMessageSender.isNsfwChannel(channel));
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

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?>[] interfaces, String methodName, Object value) {
        return (T) Proxy.newProxyInstance(
                SaucyMessageSenderTest.class.getClassLoader(),
                interfaces,
                (proxy, method, args) -> {
                    if (method.getName().equals(methodName)) {
                        return value;
                    }
                    if (method.getName().equals("toString")) {
                        return "proxy";
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(long.class)) {
                        return 0L;
                    }
                    return null;
                }
        );
    }
}
