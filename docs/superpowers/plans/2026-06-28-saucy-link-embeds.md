# Saucy Link Embeds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a SaucyBot-inspired automatic link embed pipeline for Twitter/X, Pixiv, and Misskey in every guild channel.

**Architecture:** Add a thin global JDA listener that delegates URL matching, site processing, response partitioning, and Discord delivery to focused services under `org.camelia.studio.kiss.shot.acerola.services.saucy`. Each site module owns its URL pattern and HTTP client contract, while shared services handle config, cache, ignored links, NSFW gating, temporary messages, file limits, and native embed suppression.

**Tech Stack:** Java 25, Gradle, JDA 6.4.1, Jackson Databind 2.21.2, Java `HttpClient`, JUnit 5, FFmpeg installed in the runtime Docker image.

---

## File Structure

Create a focused `services/saucy` package:

- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkEmbedConfig.java` reads `.env` values and supplies defaults.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkCache.java` provides an in-memory TTL cache.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyIgnoredContent.java` detects Saucy-style ignored content.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMatch.java` stores site id, match URL, and regex groups.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucySite.java` is the site-module interface.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyProcessResponse.java` stores text, embeds, files, and sensitivity.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyFileAttachment.java` stores uploadable bytes and filename.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyOutboundMessage.java` stores one Discord message payload after partitioning.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMessagePartitioner.java` splits responses into JDA-safe payloads.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucySiteManager.java` owns site matching and per-link processing.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMessageSender.java` sends temporary and final Discord messages.
- `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyNsfwGuard.java` decides whether a sensitive response can be posted.

Create site modules under `services/saucy/sites`:

- `FxTwitterSite.java`, `FxTwitterClient.java`, `FxTwitterModels.java`
- `PixivSite.java`, `PixivClient.java`, `PixivModels.java`, `PixivUgoiraRenderer.java`
- `MisskeySite.java`, `MisskeyClient.java`, `MisskeyModels.java`

Modify existing files:

- `src/main/java/org/camelia/studio/kiss/shot/acerola/listeners/global/SaucyLinkEmbedListener.java` is new and auto-discovered by `ListenerManager`.
- `.env.example` receives the new `SAUCY_*` variables.
- `build.gradle.kts` declares Jackson explicitly because production code will import it.
- `Dockerfile` installs FFmpeg in the runtime image.

Test files:

- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkEmbedConfigTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyIgnoredContentTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkCacheTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucySiteManagerTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMessagePartitionerTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyNsfwGuardTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/FxTwitterSiteTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivSiteTest.java`
- `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/MisskeySiteTest.java`

---

### Task 1: Dependencies And Config Surface

**Files:**
- Modify: `build.gradle.kts`
- Modify: `.env.example`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkEmbedConfig.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkEmbedConfigTest.java`

- [ ] **Step 1: Write failing config tests**

Create `SaucyLinkEmbedConfigTest.java` with tests for defaults, invalid integer fallback, and domain parsing.

```java
package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaucyLinkEmbedConfigTest {

    @Test
    void defaultsMatchSaucyBehavior() {
        SaucyLinkEmbedConfig config = SaucyLinkEmbedConfig.from(Map.of());

        assertTrue(config.enabled());
        assertEquals(3600, config.cacheTtlSeconds());
        assertEquals(8, config.maxLinksPerMessage());
        assertEquals(4, config.maxEmbedsPerMessage());
        assertEquals(10_485_760L, config.maxFileBytes());
        assertTrue(config.sendMatchedMessage());
        assertEquals("Traitement du lien en cours...", config.matchedMessage());
        assertEquals(5, config.pixivImageLimit());
        assertEquals("mp4", config.pixivUgoiraFormat());
        assertEquals(2000, config.pixivUgoiraBitrate());
        assertEquals("misskey.io", config.misskeyDomains().get(0));
        assertEquals("misskey.design", config.misskeyDomains().get(1));
        assertEquals("oekakiskey.com", config.misskeyDomains().get(2));
    }

    @Test
    void invalidNumbersFallbackToDefaults() {
        SaucyLinkEmbedConfig config = SaucyLinkEmbedConfig.from(Map.of(
                "SAUCY_MAX_LINKS_PER_MESSAGE", "0",
                "SAUCY_MAX_FILE_BYTES", "-1",
                "SAUCY_PIXIV_IMAGE_LIMIT", "abc"
        ));

        assertEquals(8, config.maxLinksPerMessage());
        assertEquals(10_485_760L, config.maxFileBytes());
        assertEquals(5, config.pixivImageLimit());
    }

    @Test
    void parsesBooleansAndMisskeyDomains() {
        SaucyLinkEmbedConfig config = SaucyLinkEmbedConfig.from(Map.of(
                "SAUCY_LINK_EMBEDS_ENABLED", "false",
                "SAUCY_SEND_MATCHED_MESSAGE", "false",
                "SAUCY_MISSKEY_DOMAINS", "misskey.io, example.social ,"
        ));

        assertFalse(config.enabled());
        assertFalse(config.sendMatchedMessage());
        assertEquals(2, config.misskeyDomains().size());
        assertEquals("example.social", config.misskeyDomains().get(1));
    }
}
```

- [ ] **Step 2: Run the failing tests**

Run: `./gradlew test --tests '*SaucyLinkEmbedConfigTest'`

Expected: fails because `SaucyLinkEmbedConfig` does not exist.

- [ ] **Step 3: Add explicit Jackson dependency**

Modify `build.gradle.kts` in `dependencies`:

```kotlin
implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
```

- [ ] **Step 4: Add `.env.example` variables**

Append this block before the DB variables:

```dotenv
SAUCY_LINK_EMBEDS_ENABLED=true
SAUCY_LINK_CACHE_TTL_SECONDS=3600
SAUCY_MAX_LINKS_PER_MESSAGE=8
SAUCY_MAX_EMBEDS_PER_MESSAGE=4
SAUCY_MAX_FILE_BYTES=10485760
SAUCY_SEND_MATCHED_MESSAGE=true
SAUCY_MATCHED_MESSAGE=Traitement du lien en cours...
SAUCY_PIXIV_SESSION_COOKIE=
SAUCY_PIXIV_IMAGE_LIMIT=5
SAUCY_PIXIV_UGOIRA_FORMAT=mp4
SAUCY_PIXIV_UGOIRA_BITRATE=2000
SAUCY_MISSKEY_DOMAINS=misskey.io,misskey.design,oekakiskey.com
```

- [ ] **Step 5: Implement config record**

Create `SaucyLinkEmbedConfig.java` with:

```java
package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public record SaucyLinkEmbedConfig(
        boolean enabled,
        int cacheTtlSeconds,
        int maxLinksPerMessage,
        int maxEmbedsPerMessage,
        long maxFileBytes,
        boolean sendMatchedMessage,
        String matchedMessage,
        String pixivSessionCookie,
        int pixivImageLimit,
        String pixivUgoiraFormat,
        int pixivUgoiraBitrate,
        List<String> misskeyDomains
) {
    private static final Logger logger = LoggerFactory.getLogger(SaucyLinkEmbedConfig.class);

    public static SaucyLinkEmbedConfig fromEnvironment() {
        return from(Configuration.getInstance().getDotenv()::get);
    }

    public static SaucyLinkEmbedConfig from(Map<String, String> values) {
        return from((key, fallback) -> values.getOrDefault(key, fallback));
    }

    private static SaucyLinkEmbedConfig from(BiFunction<String, String, String> get) {
        return new SaucyLinkEmbedConfig(
                getBoolean(get, "SAUCY_LINK_EMBEDS_ENABLED", true),
                getPositiveInt(get, "SAUCY_LINK_CACHE_TTL_SECONDS", 3600),
                getPositiveInt(get, "SAUCY_MAX_LINKS_PER_MESSAGE", 8),
                getPositiveInt(get, "SAUCY_MAX_EMBEDS_PER_MESSAGE", 4),
                getPositiveLong(get, "SAUCY_MAX_FILE_BYTES", 10_485_760L),
                getBoolean(get, "SAUCY_SEND_MATCHED_MESSAGE", true),
                get.apply("SAUCY_MATCHED_MESSAGE", "Traitement du lien en cours..."),
                get.apply("SAUCY_PIXIV_SESSION_COOKIE", ""),
                getPositiveInt(get, "SAUCY_PIXIV_IMAGE_LIMIT", 5),
                get.apply("SAUCY_PIXIV_UGOIRA_FORMAT", "mp4"),
                getPositiveInt(get, "SAUCY_PIXIV_UGOIRA_BITRATE", 2000),
                parseDomains(get.apply("SAUCY_MISSKEY_DOMAINS", "misskey.io,misskey.design,oekakiskey.com"))
        );
    }

    private static boolean getBoolean(BiFunction<String, String, String> get, String key, boolean defaultValue) {
        return Boolean.parseBoolean(get.apply(key, Boolean.toString(defaultValue)));
    }

    private static int getPositiveInt(BiFunction<String, String, String> get, String key, int defaultValue) {
        String raw = get.apply(key, Integer.toString(defaultValue));
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Fall through to the configured default.
        }
        logger.warn("Configuration {} invalide ({}), utilisation de {}", key, raw, defaultValue);
        return defaultValue;
    }

    private static long getPositiveLong(BiFunction<String, String, String> get, String key, long defaultValue) {
        String raw = get.apply(key, Long.toString(defaultValue));
        try {
            long parsed = Long.parseLong(raw);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Fall through to the configured default.
        }
        logger.warn("Configuration {} invalide ({}), utilisation de {}", key, raw, defaultValue);
        return defaultValue;
    }

    private static List<String> parseDomains(String raw) {
        List<String> domains = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return domains.isEmpty()
                ? List.of("misskey.io", "misskey.design", "oekakiskey.com")
                : domains;
    }
}
```

- [ ] **Step 6: Run config tests**

Run: `./gradlew test --tests '*SaucyLinkEmbedConfigTest'`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts .env.example src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkEmbedConfig.java src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkEmbedConfigTest.java
git commit -m "feat: add saucy embed configuration"
```

---

### Task 2: Core Matching, Ignore Rules, Cache, And Response Types

**Files:**
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyIgnoredContent.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkCache.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMatch.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucySite.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyProcessResponse.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyFileAttachment.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyIgnoredContentTest.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyLinkCacheTest.java`

- [ ] **Step 1: Write failing ignore and cache tests**

Add tests covering ignored links and TTL expiry:

```java
@Test
void ignoresAngleBracketAndSpoilerLinks() {
    assertTrue(SaucyIgnoredContent.hasIgnoredLink("look <https://x.com/a/status/1>"));
    assertTrue(SaucyIgnoredContent.hasIgnoredLink("look ||https://x.com/a/status/1||"));
    assertFalse(SaucyIgnoredContent.hasIgnoredLink("look https://x.com/a/status/1"));
}
```

```java
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
```

- [ ] **Step 2: Run failing tests**

Run: `./gradlew test --tests '*SaucyIgnoredContentTest' --tests '*SaucyLinkCacheTest'`

Expected: fails because the classes do not exist.

- [ ] **Step 3: Implement ignore rules**

`SaucyIgnoredContent` must use this regex:

```java
private static final Pattern IGNORED_LINK = Pattern.compile("(<|\\|\\|)https?://[^\\s>]+(>|\\|\\|)", Pattern.CASE_INSENSITIVE);
```

Expose:

```java
public static boolean hasIgnoredLink(String content) {
    return content != null && IGNORED_LINK.matcher(content).find();
}
```

- [ ] **Step 4: Implement cache**

`SaucyLinkCache` must:

- accept `Duration ttl` and `LongSupplier clockMillis` for tests;
- store `CacheEntry(Object value, long expiresAtMillis)` in `ConcurrentHashMap<String, CacheEntry>`;
- expose `<T> T get(String key, Supplier<T> loader)`;
- remove expired entries before replacing them.

- [ ] **Step 5: Implement shared records and interface**

Use these signatures:

```java
public record SaucyMatch(String siteId, String url, Map<String, String> groups) {}
```

```java
public interface SaucySite {
    String id();
    List<SaucyMatch> match(String content, int remainingSlots);
    CompletableFuture<Optional<SaucyProcessResponse>> process(SaucyMatch match);
}
```

```java
public record SaucyFileAttachment(String fileName, byte[] data, String contentType) {
    public long size() {
        return data.length;
    }
}
```

```java
public record SaucyProcessResponse(
        String text,
        List<MessageEmbed> embeds,
        List<SaucyFileAttachment> files,
        boolean sensitive
) {
    public boolean isEmpty() {
        return (text == null || text.isBlank()) && embeds.isEmpty() && files.isEmpty();
    }
}
```

- [ ] **Step 6: Run core tests**

Run: `./gradlew test --tests '*SaucyIgnoredContentTest' --tests '*SaucyLinkCacheTest'`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy
git commit -m "feat: add saucy core link services"
```

---

### Task 3: Site Manager, NSFW Guard, And Message Partitioning

**Files:**
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucySiteManager.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyNsfwGuard.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyOutboundMessage.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMessagePartitioner.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucySiteManagerTest.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyNsfwGuardTest.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMessagePartitionerTest.java`

- [ ] **Step 1: Write failing manager tests**

Use a fake `SaucySite` in the test and assert:

- the manager stops at `maxLinksPerMessage`;
- one site failure returns `Optional.empty()` for that link without stopping later matches.

Command: `./gradlew test --tests '*SaucySiteManagerTest'`

Expected: fails because manager does not exist.

- [ ] **Step 2: Write failing partitioner tests**

Assert:

- one text response becomes one outbound message;
- five embeds with `maxEmbedsPerMessage=4` become two outbound messages;
- files are grouped until `maxFileBytes` would be exceeded.

Command: `./gradlew test --tests '*SaucyMessagePartitionerTest'`

Expected: fails because partitioner does not exist.

- [ ] **Step 3: Implement manager**

`SaucySiteManager` constructor:

```java
public SaucySiteManager(List<SaucySite> sites, SaucyLinkEmbedConfig config)
```

Methods:

```java
public List<SaucyMatch> match(String content)
public CompletableFuture<List<SaucyProcessResponse>> process(String content)
```

`match` loops sites in constructor order and decrements remaining slots until zero.

`process` loops over matches, catches exceptions per match, logs failures, and returns successful non-empty responses.

- [ ] **Step 4: Implement NSFW guard**

`SaucyNsfwGuard` exposes pure method:

```java
public boolean canPost(boolean sensitive, boolean channelNsfw) {
    return !sensitive || channelNsfw;
}
```

Test all four boolean combinations.

- [ ] **Step 5: Implement outbound records and partitioning**

`SaucyOutboundMessage`:

```java
public record SaucyOutboundMessage(String text, List<MessageEmbed> embeds, List<SaucyFileAttachment> files) {
    public boolean isEmpty() {
        return (text == null || text.isBlank()) && embeds.isEmpty() && files.isEmpty();
    }
}
```

`SaucyMessagePartitioner` constructor:

```java
public SaucyMessagePartitioner(int maxEmbedsPerMessage, long maxFileBytes)
```

Rules:

- if response has text and no embeds/files, return one text message;
- if response has text and files, send text first;
- split embeds into chunks of `maxEmbedsPerMessage`;
- split files into chunks whose summed `size()` stays below `maxFileBytes`;
- do not emit empty messages.

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests '*SaucySiteManagerTest' --tests '*SaucyNsfwGuardTest' --tests '*SaucyMessagePartitionerTest'`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy
git commit -m "feat: add saucy processing pipeline"
```

---

### Task 4: FxTwitter Site And Client

**Files:**
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/FxTwitterClient.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/FxTwitterModels.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/FxTwitterSite.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/FxTwitterSiteTest.java`

- [ ] **Step 1: Write failing site tests**

Test these behaviors with a fake client:

- matches `https://twitter.com/user/status/123`, `https://x.com/user/status/123`, and `https://mobile.twitter.com/user/status/123`;
- regular tweet produces one embed with four stat fields;
- tweet with two photos produces two embeds;
- `possibly_sensitive=true` produces a sensitive response;
- video over `maxFileBytes` returns text fallback `https://fxtwitter.com/user/status/123`.

Run: `./gradlew test --tests '*FxTwitterSiteTest'`

Expected: fails because site classes do not exist.

- [ ] **Step 2: Implement models**

Use Jackson annotations matching Saucy response fields:

```java
record FxTwitterResponse(@JsonProperty("tweet") FxTwitterTweet tweet) {}
record FxTwitterTweet(
        @JsonProperty("id") String id,
        @JsonProperty("url") String url,
        @JsonProperty("text") String text,
        @JsonProperty("created_timestamp") long createdTimestamp,
        @JsonProperty("author") FxTwitterAuthor author,
        @JsonProperty("replies") Integer replies,
        @JsonProperty("retweets") Integer retweets,
        @JsonProperty("likes") Integer likes,
        @JsonProperty("views") Integer views,
        @JsonProperty("possibly_sensitive") boolean possiblySensitive,
        @JsonProperty("media") FxTwitterMedia media,
        @JsonProperty("translation") FxTwitterTranslation translation,
        @JsonProperty("quote") FxTwitterTweet quotedTweet
) {}
```

Add the remaining records with these fields:

```java
record FxTwitterAuthor(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("screen_name") String screenName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("url") String url
) {}
record FxTwitterMedia(
        @JsonProperty("photos") List<FxTwitterPhoto> photos,
        @JsonProperty("videos") List<FxTwitterVideo> videos
) {}
record FxTwitterPhoto(
        @JsonProperty("type") String type,
        @JsonProperty("url") String url,
        @JsonProperty("width") int width,
        @JsonProperty("height") int height
) {}
record FxTwitterVideo(
        @JsonProperty("type") String type,
        @JsonProperty("url") String url,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("width") int width,
        @JsonProperty("height") int height,
        @JsonProperty("format") String format
) {}
record FxTwitterTranslation(
        @JsonProperty("text") String text,
        @JsonProperty("source_lang") String sourceLanguage,
        @JsonProperty("target_lang") String targetLanguage
) {}
```

- [ ] **Step 3: Implement client**

`FxTwitterClient`:

- constructor accepts `HttpClient`, `ObjectMapper`, and `SaucyLinkCache`;
- method `Optional<FxTwitterResponse> getTweet(String user, String id, String translate)`;
- builds URL `https://api.fxtwitter.com/{user}/status/{id}` and appends `/{translate}` when present;
- caches JSON by key `fxtwitter:{user}:{id}:{translate}`;
- returns `Optional.empty()` for non-2xx HTTP status or JSON parsing failure.

- [ ] **Step 4: Implement site**

`FxTwitterSite`:

- regex: `https?://(www\\.|mobile\\.)?(?<domain>twitter|x|nitter)\\.(com|net)/(?<user>[^/]+)/status/(?<id>\\d+)(/(video|photo)/\\d)?(/(?<translate>\\w{2}|\\w{5}))?`
- builds `EmbedBuilder` with color `0x1DA1F2`;
- uses translated text when `translation` exists;
- adds fields `Replies`, `Retweets`, `Likes`, `Views`;
- sets image URL for photos, replacing or appending `name=orig` when possible;
- for video/gif, downloads bytes only when `Content-Length` is under config max file bytes;
- returns fallback text when video cannot be uploaded.

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests '*FxTwitterSiteTest'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites
git commit -m "feat: add fxtwitter saucy embeds"
```

---

### Task 5: Misskey Site And Client

**Files:**
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/MisskeyClient.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/MisskeyModels.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/MisskeySite.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/MisskeySiteTest.java`

- [ ] **Step 1: Write failing Misskey tests**

Test:

- domains come from config;
- a two-image note produces two embeds;
- a video file is skipped;
- `isSensitive=true` marks response sensitive;
- unknown domain does not match.

Run: `./gradlew test --tests '*MisskeySiteTest'`

Expected: fails because site does not exist.

- [ ] **Step 2: Implement models**

Use records:

```java
record MisskeyNote(
        String id,
        String createdAt,
        String text,
        String visibility,
        List<MisskeyFile> files,
        MisskeyUser user
) {}
record MisskeyUser(String id, String name, String username, String avatarUrl) {}
record MisskeyFile(String id, String type, int size, boolean isSensitive, String url, String thumbnailUrl) {}
```

- [ ] **Step 3: Implement client**

`MisskeyClient`:

- POSTs a JSON object with one property named `noteId` and the matched note id as the value to `{baseUrl}/api/notes/show`;
- caches response key `misskey:{baseUrl}:{id}`;
- uses Jackson for serialization/deserialization;
- returns `Optional.empty()` for non-2xx or malformed JSON.

- [ ] **Step 4: Implement site**

`MisskeySite`:

- builds escaped domain alternation from `config.misskeyDomains()`;
- regex template after domain escaping: `(?<url>https?://(www\\.)?(misskey\\.io|misskey\\.design|oekakiskey\\.com))/notes/(?<id>[0-9a-z]+)` for the default config;
- creates one embed per file where `type.startsWith("image/")`;
- sets color `0x85B300`, author, avatar, URL, timestamp, description, image and footer `Misskey`;
- response is sensitive when any file has `isSensitive=true`.

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests '*MisskeySiteTest'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites
git commit -m "feat: add misskey saucy embeds"
```

---

### Task 6: Pixiv Static Images And Client

**Files:**
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivClient.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivModels.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivSite.java`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivSiteTest.java`

- [ ] **Step 1: Write failing Pixiv static tests**

Test:

- matches `https://www.pixiv.net/en/artworks/106848609`;
- absent `SAUCY_PIXIV_SESSION_COOKIE` returns empty processing result;
- single-page illustration produces one file;
- four-page illustration produces four files;
- ten-page illustration with limit five produces five files and text `This is part of a 10 image set.`;
- `xRestrict > 0` marks response sensitive.

Run: `./gradlew test --tests '*PixivSiteTest'`

Expected: fails because Pixiv classes do not exist.

- [ ] **Step 2: Implement models**

Use records mirroring Pixiv ajax JSON:

```java
record PixivIllustrationResponse(boolean error, String message, PixivIllustration body) {}
record PixivIllustration(
        String id,
        String title,
        String description,
        int illustType,
        int pageCount,
        int xRestrict,
        PixivIllustrationUrls urls
) {}
record PixivIllustrationUrls(String mini, String thumb, String small, String regular, String original) {}
record PixivPagesResponse(boolean error, String message, List<PixivPage> body) {}
record PixivPage(PixivPageUrls urls, int width, int height) {}
record PixivPageUrls(String thumb_mini, String small, String regular, String original) {}
```

- [ ] **Step 3: Implement Pixiv client**

`PixivClient`:

- builds a Java `HttpClient` with `Cookie` header `PHPSESSID=<cookie>`;
- sets `User-Agent` to a modern browser string and `Referer` to `https://www.pixiv.net/`;
- `boolean hasSessionCookie()`;
- `Optional<PixivIllustrationResponse> illustrationDetails(String id)`;
- `Optional<PixivPagesResponse> illustrationPages(String id)`;
- `Optional<byte[]> download(String url)`;
- `long contentLength(String url)` using HEAD when available;
- caches JSON responses with `SaucyLinkCache`.

- [ ] **Step 4: Implement static Pixiv site behavior**

`PixivSite`:

- regex `https?://(www\\.)?pixiv\\.net/.*/artworks/(?<id>\\d+)/?`;
- ignores processing when client lacks a session cookie;
- for `illustType != 2`, uses original, regular, small, thumbnail order;
- chooses first URL under `config.maxFileBytes()`;
- downloads files and names them from URL path;
- caps multi-page downloads to `config.pixivImageLimit()`;
- marks response sensitive when `xRestrict > 0`.

- [ ] **Step 5: Run Pixiv static tests**

Run: `./gradlew test --tests '*PixivSiteTest'`

Expected: PASS for static-image tests. Ugoira tests are added in Task 7.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites
git commit -m "feat: add pixiv image embeds"
```

---

### Task 7: Pixiv Ugoira Rendering And Docker FFmpeg

**Files:**
- Modify: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivModels.java`
- Modify: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivClient.java`
- Modify: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivSite.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivUgoiraRenderer.java`
- Modify: `Dockerfile`
- Test: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites/PixivSiteTest.java`

- [ ] **Step 1: Add failing ugoira tests**

Use a fake renderer in the test:

- illustration with `illustType=2` calls renderer;
- renderer output under max file size becomes one file;
- renderer output over max file size returns empty response;
- response remains sensitive when `xRestrict > 0`.

Run: `./gradlew test --tests '*PixivSiteTest'`

Expected: fails because renderer is not wired.

- [ ] **Step 2: Extend models and client**

Add:

```java
record PixivUgoiraMetadataResponse(boolean error, String message, PixivUgoiraMetadata body) {}
record PixivUgoiraMetadata(String originalSrc, String src, String mime_type, List<PixivUgoiraFrame> frames) {}
record PixivUgoiraFrame(String file, int delay) {}
```

Client method:

```java
Optional<PixivUgoiraMetadataResponse> ugoiraMetadata(String id);
```

- [ ] **Step 3: Implement renderer**

`PixivUgoiraRenderer`:

- creates a temp directory with `Files.createTempDirectory("kiss-shot-pixiv-ugoira-")`;
- extracts downloaded zip bytes with `ZipInputStream`;
- writes `ffconcat` file:

```text
ffconcat version 1.0
file frame1.jpg
duration 0.08
file frame2.jpg
duration 0.08
file frame2.jpg
```

- runs:

```bash
ffmpeg -y -f concat -i <concat> -b:v <bitrate>k -pix_fmt yuv420p -filter:v pad=ceil(iw/2)*2:ceil(ih/2)*2 <output>
```

- reads output bytes and deletes the temp directory in `finally`.

- [ ] **Step 4: Wire Pixiv site**

For `illustType == 2`:

- call metadata endpoint;
- download `originalSrc`;
- render video with configured format and bitrate;
- return one file named `<title>_ugoira.<format>` when size is under max.

- [ ] **Step 5: Install FFmpeg in Dockerfile**

Modify runtime apt install block:

```dockerfile
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libssl3 \
    libgcc-s1 \
    && rm -rf /var/lib/apt/lists/*
```

- [ ] **Step 6: Run Pixiv tests and Docker-sensitive build**

Run:

```bash
./gradlew test --tests '*PixivSiteTest'
./gradlew compileJava
./gradlew shadowJar
```

Expected: all commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add Dockerfile src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy/sites
git commit -m "feat: add pixiv ugoira rendering"
```

---

### Task 8: Discord Sender And Global Listener

**Files:**
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy/SaucyMessageSender.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/listeners/global/SaucyLinkEmbedListener.java`
- Modify only if needed: no changes to `ListenerManager`, because reflection already scans `listeners/global`.

- [ ] **Step 1: Implement sender**

`SaucyMessageSender` constructor:

```java
public SaucyMessageSender(SaucyLinkEmbedConfig config, SaucyMessagePartitioner partitioner, SaucyNsfwGuard nsfwGuard)
```

Core method:

```java
public void send(Message sourceMessage, List<SaucyProcessResponse> responses)
```

Behavior:

- if `config.sendMatchedMessage()` send `sourceMessage.reply(config.matchedMessage())`;
- use `.mentionRepliedUser(false).setAllowedMentions(List.of())` on every message create action;
- for each response, compute channel NSFW with `sourceMessage.getGuildChannel() instanceof IAgeRestrictedChannel ageRestricted && ageRestricted.isNSFW()`;
- skip sensitive responses in non-NSFW channels and log site/message information from caller context;
- build `MessageCreateBuilder` per `SaucyOutboundMessage`;
- convert files with `FileUpload.fromData(file.data(), file.fileName())`;
- reply with `sourceMessage.reply(builder.build())`;
- track whether at least one final message was sent successfully;
- delete temporary message in success and failure callbacks;
- call `sourceMessage.suppressEmbeds(true)` only after at least one final message succeeds and `sourceMessage.getGuild().getSelfMember().hasPermission(sourceMessage.getGuildChannel(), Permission.MESSAGE_MANAGE)`.

- [ ] **Step 2: Implement listener**

`SaucyLinkEmbedListener`:

- constructor builds config, cache, clients, sites, manager, partitioner, guard, sender;
- `onMessageReceived` returns when disabled, not guild, bot author, or ignored content;
- calls `siteManager.process(content).thenAccept(responses -> sender.send(event.getMessage(), responses))`;
- logs exceptions with message id and channel id.

Use:

```java
if (!event.isFromGuild()) return;
if (event.getAuthor().isBot()) return;
String content = event.getMessage().getContentRaw();
if (SaucyIgnoredContent.hasIgnoredLink(content)) return;
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy src/main/java/org/camelia/studio/kiss/shot/acerola/listeners/global/SaucyLinkEmbedListener.java
git commit -m "feat: wire saucy link listener"
```

---

### Task 9: Full Verification And Polish

**Files:**
- Modify only files needed to fix failures found by verification.

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`

Expected: PASS.

- [ ] **Step 2: Run compiler**

Run: `./gradlew compileJava`

Expected: PASS.

- [ ] **Step 3: Build the shadow jar**

Run: `./gradlew shadowJar`

Expected: PASS and `build/libs/kiss-shot-acerola.jar` exists.

- [ ] **Step 4: Inspect the fat jar**

Run: `jar tf build/libs/kiss-shot-acerola.jar | rg 'SaucyLinkEmbedListener|FxTwitterSite|PixivSite|MisskeySite|jackson'`

Expected: output includes the listener, all three site classes, and Jackson classes.

- [ ] **Step 5: Inspect git diff**

Run: `git status --short && git diff --stat`

Expected: only intended Saucy embed files, `.env.example`, `build.gradle.kts`, and `Dockerfile` are changed.

- [ ] **Step 6: Commit final polish if any files changed**

```bash
git add build.gradle.kts .env.example Dockerfile src/main/java/org/camelia/studio/kiss/shot/acerola/listeners/global/SaucyLinkEmbedListener.java src/main/java/org/camelia/studio/kiss/shot/acerola/services/saucy src/test/java/org/camelia/studio/kiss/shot/acerola/services/saucy
git commit -m "fix: polish saucy link embeds"
```

Run `git status --short` first and skip this commit when there are no modified files after verification.

---

## Self-Review

Spec coverage:

- Global guild-channel activation is covered by Task 8.
- Successful replacement before native embed suppression is covered by Task 8.
- Saucy ignore rules are covered by Task 2.
- Configurable limits and cache TTL are covered by Tasks 1 and 2.
- Twitter/X via FxTwitter is covered by Task 4.
- Misskey configurable domains are covered by Task 5.
- Pixiv images, ugoira, and FFmpeg are covered by Tasks 6 and 7.
- NSFW gating is covered by Task 3 and wired in Task 8.
- Docker FFmpeg is covered by Task 7.
- Full validation is covered by Task 9.

Unresolved-marker scan:

- No unresolved markers are allowed in this plan.
- Every task has exact files, commands, expected results, and concrete class or method signatures.

Type consistency:

- Shared response type is `SaucyProcessResponse`.
- Upload type is `SaucyFileAttachment`.
- One sendable Discord payload is `SaucyOutboundMessage`.
- Site contract is `SaucySite`.
- The listener uses `SaucySiteManager` and `SaucyMessageSender`.
