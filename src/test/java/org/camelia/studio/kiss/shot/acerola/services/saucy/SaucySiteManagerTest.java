package org.camelia.studio.kiss.shot.acerola.services.saucy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.dv8tion.jda.api.EmbedBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SaucySiteManagerTest {

    @Test
    void matchStopsAtConfiguredMaximumAndIgnoresOverReturnedMatches() {
        FakeSite first = new FakeSite("first",
                List.of(match("first", "1"), match("first", "2"), match("first", "3")));
        FakeSite second = new FakeSite("second",
                List.of(match("second", "1"), match("second", "2")));
        SaucySiteManager manager = new SaucySiteManager(
                List.of(first, second),
                configWithMaxLinks(4)
        );

        List<SaucyMatch> matches = manager.match("content");

        assertEquals(4, matches.size());
        assertEquals(List.of(4), first.remainingSlotsSeen);
        assertEquals(List.of(1), second.remainingSlotsSeen);
        assertEquals("first", matches.get(0).siteId());
        assertEquals("first", matches.get(1).siteId());
        assertEquals("first", matches.get(2).siteId());
        assertEquals("second", matches.get(3).siteId());
    }

    @Test
    void processKeepsOnlySuccessfulNonEmptyResponsesInMatchOrder() {
        FakeSite site = new FakeSite("site", List.of(
                match("site", "success-1"),
                match("site", "empty"),
                match("site", "throwing"),
                match("site", "success-2")
        ));
        SaucyProcessResponse first = response("first");
        SaucyProcessResponse empty = new SaucyProcessResponse(null, List.of(), List.of(), false);
        SaucyProcessResponse second = response("second");
        site.response("success-1", CompletableFuture.completedFuture(Optional.of(first)));
        site.response("empty", CompletableFuture.completedFuture(Optional.of(empty)));
        site.response("throwing", CompletableFuture.failedFuture(new IllegalStateException("boom")));
        site.response("success-2", CompletableFuture.completedFuture(Optional.of(second)));
        SaucySiteManager manager = new SaucySiteManager(List.of(site), configWithMaxLinks(8));

        List<SaucyProcessResponse> responses = manager.process("content").join();

        assertEquals(List.of(first, second), responses);
    }

    @Test
    void skipsThrowingMatcherAndContinuesWithLaterSites() {
        ThrowingSite throwing = new ThrowingSite("throwing");
        FakeSite later = new FakeSite("later", List.of(match("later", "success")));
        SaucyProcessResponse response = response("later response");
        later.response("success", CompletableFuture.completedFuture(Optional.of(response)));
        SaucySiteManager manager = new SaucySiteManager(List.of(throwing, later), configWithMaxLinks(8));

        List<SaucyMatch> matches = assertDoesNotThrow(() -> manager.match("content"));
        CompletableFuture<List<SaucyProcessResponse>> processFuture =
                assertDoesNotThrow(() -> manager.process("content"));

        assertEquals(List.of(match("later", "success")), matches);
        assertEquals(List.of(response), processFuture.join());
    }

    @Test
    void processLogsStartAndIgnoredMatches() {
        FakeSite site = new FakeSite("site", List.of(
                match("site", "empty-optional"),
                match("site", "empty-response"),
                match("site", "success")
        ));
        SaucyProcessResponse empty = new SaucyProcessResponse(null, List.of(), List.of(), false);
        SaucyProcessResponse response = response("success response");
        site.response("empty-optional", CompletableFuture.completedFuture(Optional.empty()));
        site.response("empty-response", CompletableFuture.completedFuture(Optional.of(empty)));
        site.response("success", CompletableFuture.completedFuture(Optional.of(response)));
        SaucySiteManager manager = new SaucySiteManager(List.of(site), configWithMaxLinks(8));
        CapturedLogs logs = captureLogs(Level.INFO);

        try {
            List<SaucyProcessResponse> responses = manager.process("content").join();

            assertEquals(List.of(response), responses);
            assertEquals(true, logs.contains(Level.INFO, "Starting saucy processing for 3 matched link(s)"));
            assertEquals(true, logs.contains(Level.INFO, "Ignoring saucy match empty-optional for site site: no response"));
            assertEquals(true, logs.contains(Level.INFO, "Ignoring saucy match empty-response for site site: empty response"));
        } finally {
            logs.close();
        }
    }

    @Test
    void processLogsWhenNoSupportedLinkMatched() {
        FakeSite site = new FakeSite("site", List.of());
        SaucySiteManager manager = new SaucySiteManager(List.of(site), configWithMaxLinks(8));
        CapturedLogs logs = captureLogs(Level.DEBUG);

        try {
            List<SaucyProcessResponse> responses = manager.process("content").join();

            assertEquals(List.of(), responses);
            assertEquals(true, logs.contains(Level.DEBUG, "Ignoring saucy content: no supported link matched"));
        } finally {
            logs.close();
        }
    }

    private static SaucyLinkEmbedConfig configWithMaxLinks(int maxLinks) {
        return new SaucyLinkEmbedConfig(
                true,
                3600,
                maxLinks,
                4,
                10_485_760L,
                true,
                "Traitement du lien en cours...",
                "",
                5,
                "mp4",
                2000,
                List.of("misskey.io")
        );
    }

    private static SaucyMatch match(String siteId, String url) {
        return new SaucyMatch(siteId, url, Map.of());
    }

    private static SaucyProcessResponse response(String text) {
        return new SaucyProcessResponse(
                text,
                List.of(new EmbedBuilder().setDescription(text).build()),
                List.of(),
                false
        );
    }

    private static CapturedLogs captureLogs(Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(SaucySiteManager.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(level);
        logger.addAppender(appender);
        return new CapturedLogs(logger, previousLevel, appender);
    }

    private record CapturedLogs(Logger logger, Level previousLevel, ListAppender<ILoggingEvent> appender)
            implements AutoCloseable {
        private boolean contains(Level level, String message) {
            return appender.list.stream().anyMatch(event ->
                    event.getLevel() == level && event.getFormattedMessage().contains(message)
            );
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    private static final class FakeSite implements SaucySite {
        private final String id;
        private final List<SaucyMatch> matches;
        private final Map<String, CompletableFuture<Optional<SaucyProcessResponse>>> responses =
                new java.util.HashMap<>();
        private final List<Integer> remainingSlotsSeen = new ArrayList<>();

        private FakeSite(String id, List<SaucyMatch> matches) {
            this.id = id;
            this.matches = matches;
        }

        private void response(String url, CompletableFuture<Optional<SaucyProcessResponse>> response) {
            responses.put(url, response);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<SaucyMatch> match(String content, int remainingSlots) {
            remainingSlotsSeen.add(remainingSlots);
            return matches;
        }

        @Override
        public CompletableFuture<Optional<SaucyProcessResponse>> process(SaucyMatch match) {
            return responses.getOrDefault(match.url(), CompletableFuture.completedFuture(Optional.empty()));
        }
    }

    private static final class ThrowingSite implements SaucySite {
        private final String id;

        private ThrowingSite(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<SaucyMatch> match(String content, int remainingSlots) {
            throw new IllegalStateException("match failed");
        }

        @Override
        public CompletableFuture<Optional<SaucyProcessResponse>> process(SaucyMatch match) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
