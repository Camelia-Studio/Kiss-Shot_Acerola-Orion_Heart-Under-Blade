package org.camelia.studio.kiss.shot.acerola.services.saucy;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
        AtomicReference<T> value = new AtomicReference<>();
        cache.compute(key, (ignored, existing) -> {
            if (existing != null && existing.expiresAtMillis() > now) {
                value.set(existing.value());
                return existing;
            }

            T loaded = loader.get();
            value.set(loaded);
            if (loaded == null) {
                return null;
            }

            return new CacheEntry<>(loaded, now + ttl.toMillis());
        });
        return value.get();
    }

    public T getIfPresent(String key) {
        long now = clockMillis.getAsLong();
        AtomicReference<T> value = new AtomicReference<>();
        cache.computeIfPresent(key, (ignored, existing) -> {
            if (existing.expiresAtMillis() <= now) {
                return null;
            }

            value.set(existing.value());
            return existing;
        });
        return value.get();
    }

    public void put(String key, T value) {
        if (value == null) {
            cache.remove(key);
            return;
        }

        long now = clockMillis.getAsLong();
        cache.put(key, new CacheEntry<>(value, now + ttl.toMillis()));
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}
