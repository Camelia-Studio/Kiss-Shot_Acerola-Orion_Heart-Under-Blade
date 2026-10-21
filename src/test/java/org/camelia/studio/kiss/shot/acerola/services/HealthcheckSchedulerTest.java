package org.camelia.studio.kiss.shot.acerola.services;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthcheckSchedulerTest {
    private static final URI URL = URI.create("https://status.example.test/api/push/abc");

    private final List<URI> pushes = new ArrayList<>();

    @Test
    void isDisabledWhenUrlIsBlank() {
        assertTrue(HealthcheckScheduler.fromUrl(null, () -> true).isEmpty());
        assertTrue(HealthcheckScheduler.fromUrl("  ", () -> true).isEmpty());
    }

    @Test
    void rejectsNonHttpUrl() {
        assertThrows(IllegalStateException.class, () -> HealthcheckScheduler.fromUrl("ftp://example.test", () -> true));
    }

    @Test
    void pushesImmediatelyThenEvery45SecondsOnce() {
        RecordingExecutor executor = new RecordingExecutor();
        try (HealthcheckScheduler scheduler = new HealthcheckScheduler(URL, this::record, () -> true, executor)) {
            scheduler.start(jda(JDA.Status.CONNECTED));
            scheduler.start(jda(JDA.Status.CONNECTED));

            assertEquals(1, executor.calls);
            assertEquals(0, executor.initialDelay);
            assertEquals(45, executor.period);
            assertEquals(TimeUnit.SECONDS, executor.unit);

            executor.command.run();
            assertEquals(List.of(URL), pushes);
        }
        assertTrue(executor.isShutdown());
    }

    @Test
    void skipsPushWhenDiscordIsNotConnected() {
        scheduler(() -> true).push(jda(JDA.Status.RECONNECT_QUEUED));

        assertTrue(pushes.isEmpty());
    }

    @Test
    void skipsPushWhenApiIsUnavailable() {
        scheduler(() -> false).push(jda(JDA.Status.CONNECTED));

        assertTrue(pushes.isEmpty());
    }

    @Test
    void failuresDoNotEscapeThePush() {
        HealthcheckScheduler failingSender = new HealthcheckScheduler(URL, url -> {
            throw new IOException("boom");
        }, () -> true, new RecordingExecutor());
        HealthcheckScheduler failingProbe = scheduler(() -> {
            throw new IllegalStateException("boom");
        });

        assertDoesNotThrow(() -> failingSender.push(jda(JDA.Status.CONNECTED)));
        assertDoesNotThrow(() -> failingProbe.push(jda(JDA.Status.CONNECTED)));
    }

    private HealthcheckScheduler scheduler(java.util.function.BooleanSupplier apiAvailable) {
        return new HealthcheckScheduler(URL, this::record, apiAvailable, new RecordingExecutor());
    }

    private int record(URI url) {
        pushes.add(url);
        return 200;
    }

    private static JDA jda(JDA.Status status) {
        return (JDA) Proxy.newProxyInstance(
                JDA.class.getClassLoader(),
                new Class[]{JDA.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> status;
                    case "toString" -> "JDA[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class RecordingExecutor extends ScheduledThreadPoolExecutor {
        private Runnable command;
        private long initialDelay = -1;
        private long period;
        private TimeUnit unit;
        private int calls;

        private RecordingExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            this.command = command;
            this.initialDelay = initialDelay;
            this.period = period;
            this.unit = unit;
            calls++;
            return null;
        }
    }
}
