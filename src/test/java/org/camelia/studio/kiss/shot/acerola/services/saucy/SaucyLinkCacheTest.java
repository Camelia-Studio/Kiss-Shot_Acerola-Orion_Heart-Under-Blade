package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaucyLinkCacheTest {

    @Test
    void expiresCachedValueAfterTtl() {
        AtomicLong now = new AtomicLong(1_000);
        SaucyLinkCache<String> cache = new SaucyLinkCache<>(Duration.ofSeconds(1), now::get);

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
        SaucyLinkCache<String> cache = new SaucyLinkCache<>(Duration.ofSeconds(10), now::get);

        cache.get("key", () -> "value-" + calls.incrementAndGet());
        cache.get("key", () -> "value-" + calls.incrementAndGet());
        now.addAndGet(10_001);
        cache.get("key", () -> "value-" + calls.incrementAndGet());

        assertEquals(2, calls.get());
    }

    @Test
    void nullLoaderResultIsNotCached() {
        SaucyLinkCache<String> cache = new SaucyLinkCache<>(Duration.ofSeconds(10), () -> 1_000);
        AtomicInteger calls = new AtomicInteger();

        String first = cache.get("key", () -> {
            calls.incrementAndGet();
            return null;
        });
        String second = cache.get("key", () -> "value-" + calls.incrementAndGet());

        assertEquals(null, first);
        assertEquals("value-2", second);
        assertEquals(2, calls.get());
    }

    @Test
    void returnsTypedValuesWithoutSharedMapCasts() {
        SaucyLinkCache<Integer> cache = new SaucyLinkCache<>(Duration.ofSeconds(10), () -> 1_000);

        Integer value = cache.get("key", () -> 42);

        assertEquals(42, value);
    }

    @Test
    void coalescesConcurrentSameKeyMisses() throws Exception {
        SaucyLinkCache<String> cache = new SaucyLinkCache<>(Duration.ofSeconds(10), () -> 1_000);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> {
                start.await();
                return cache.get("key", () -> loadValue(calls));
            });
            Future<String> second = executor.submit(() -> {
                start.await();
                return cache.get("key", () -> loadValue(calls));
            });

            start.countDown();

            assertEquals("value-1", get(first));
            assertEquals("value-1", get(second));
            assertEquals(1, calls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static String loadValue(AtomicInteger calls) {
        int call = calls.incrementAndGet();
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        return "value-" + call;
    }

    private static String get(Future<String> future) throws ExecutionException, InterruptedException {
        return future.get();
    }
}
