package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaucyLinkCacheTest {

    @Test
    void expiresCachedValueAfterTtl() {
        AtomicLong now = new AtomicLong(1_000);
        SaucyLinkCache cache = new SaucyLinkCache(Duration.ofSeconds(1), now::get);

        String first = cache.get("key", () -> "value-1");
        now.addAndGet(500);
        String second = cache.get("key", () -> "value-2");
        now.addAndGet(600);
        String third = cache.get("key", () -> "value-3");

        assertEquals("value-1", first);
        assertEquals("value-1", second);
        assertEquals("value-3", third);
    }

    @Test
    void loaderRunsOnlyWhenMissingOrExpired() {
        AtomicLong now = new AtomicLong(1_000);
        AtomicInteger calls = new AtomicInteger();
        SaucyLinkCache cache = new SaucyLinkCache(Duration.ofSeconds(10), now::get);

        cache.get("key", () -> "value-" + calls.incrementAndGet());
        cache.get("key", () -> "value-" + calls.incrementAndGet());
        now.addAndGet(10_001);
        cache.get("key", () -> "value-" + calls.incrementAndGet());

        assertEquals(2, calls.get());
    }
}
