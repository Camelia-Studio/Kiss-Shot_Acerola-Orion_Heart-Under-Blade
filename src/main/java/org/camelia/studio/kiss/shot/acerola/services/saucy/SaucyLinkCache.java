package org.camelia.studio.kiss.shot.acerola.services.saucy;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class SaucyLinkCache {
    private final Duration ttl;
    private final LongSupplier clockMillis;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SaucyLinkCache(Duration ttl, LongSupplier clockMillis) {
        this.ttl = ttl;
        this.clockMillis = clockMillis;
    }

    public SaucyLinkCache(Duration ttl) {
        this(ttl, System::currentTimeMillis);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        long now = clockMillis.getAsLong();
        CacheEntry existing = cache.get(key);
        if (existing != null && existing.expiresAtMillis() > now) {
            return (T) existing.value();
        }

        if (existing != null) {
            cache.remove(key, existing);
        }

        T loaded = loader.get();
        cache.put(key, new CacheEntry(loaded, now + ttl.toMillis()));
        return loaded;
    }

    private record CacheEntry(Object value, long expiresAtMillis) {
    }
}
