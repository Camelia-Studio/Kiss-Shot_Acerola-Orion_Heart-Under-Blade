package org.camelia.studio.kiss.shot.acerola.services.saucy;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class SaucyLinkCache<T> {
    private final Duration ttl;
    private final LongSupplier clockMillis;
    private final ConcurrentHashMap<String, CacheEntry<T>> cache = new ConcurrentHashMap<>();

    public SaucyLinkCache(Duration ttl, LongSupplier clockMillis) {
        this.ttl = ttl;
        this.clockMillis = clockMillis;
    }

    public SaucyLinkCache(Duration ttl) {
        this(ttl, System::currentTimeMillis);
    }

    public T get(String key, Supplier<T> loader) {
        long now = clockMillis.getAsLong();
        return cache.compute(key, (ignored, existing) -> {
            if (existing != null && existing.expiresAtMillis() > now) {
                return existing;
            }

            T loaded = loader.get();
            return new CacheEntry<>(loaded, now + ttl.toMillis());
        }).value();
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}
