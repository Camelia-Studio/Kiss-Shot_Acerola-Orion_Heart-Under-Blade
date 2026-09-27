package org.camelia.studio.kiss.shot.acerola.services.saucy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class SaucyLinkCache<T> {
    private static final int DEFAULT_MAX_ENTRIES = 2000;

    private final Duration ttl;
    private final LongSupplier clockMillis;
    private final Map<String, CacheEntry<T>> cache;

    public SaucyLinkCache(Duration ttl, LongSupplier clockMillis, int maxEntries) {
        this.ttl = ttl;
        this.clockMillis = clockMillis;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<T>> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public SaucyLinkCache(Duration ttl, LongSupplier clockMillis) {
        this(ttl, clockMillis, DEFAULT_MAX_ENTRIES);
    }

    public SaucyLinkCache(Duration ttl) {
        this(ttl, System::currentTimeMillis);
    }

    public synchronized T get(String key, Supplier<T> loader) {
        long now = clockMillis.getAsLong();
        evictExpired(now);

        CacheEntry<T> existing = cache.get(key);
        if (existing != null && existing.expiresAtMillis() > now) {
            return existing.value();
        }

        T loaded = loader.get();
        if (loaded == null) {
            cache.remove(key);
            return null;
        }

        cache.put(key, new CacheEntry<>(loaded, now + ttl.toMillis()));
        return loaded;
    }

    public synchronized T getIfPresent(String key) {
        long now = clockMillis.getAsLong();
        CacheEntry<T> existing = cache.get(key);
        if (existing == null) {
            return null;
        }
        if (existing.expiresAtMillis() <= now) {
            cache.remove(key);
            return null;
        }

        return existing.value();
    }

    public synchronized void put(String key, T value) {
        long now = clockMillis.getAsLong();
        evictExpired(now);

        if (value == null) {
            cache.remove(key);
            return;
        }

        cache.put(key, new CacheEntry<>(value, now + ttl.toMillis()));
    }

    private void evictExpired(long now) {
        cache.values().removeIf(entry -> entry.expiresAtMillis() <= now);
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}
