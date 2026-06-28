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

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_CACHE_TTL_SECONDS = 3600;
    private static final int DEFAULT_MAX_LINKS_PER_MESSAGE = 8;
    private static final int DEFAULT_MAX_EMBEDS_PER_MESSAGE = 4;
    private static final long DEFAULT_MAX_FILE_BYTES = 10_485_760L;
    private static final boolean DEFAULT_SEND_MATCHED_MESSAGE = true;
    private static final String DEFAULT_MATCHED_MESSAGE = "Traitement du lien en cours...";
    private static final String DEFAULT_PIXIV_SESSION_COOKIE = "";
    private static final int DEFAULT_PIXIV_IMAGE_LIMIT = 5;
    private static final String DEFAULT_PIXIV_UGOIRA_FORMAT = "mp4";
    private static final int DEFAULT_PIXIV_UGOIRA_BITRATE = 2000;
    private static final List<String> DEFAULT_MISSKEY_DOMAINS = List.of(
            "misskey.io",
            "misskey.design",
            "oekakiskey.com"
    );

    public static SaucyLinkEmbedConfig fromEnvironment() {
        return from(Configuration.getInstance().getDotenv()::get);
    }

    public static SaucyLinkEmbedConfig from(Map<String, String> values) {
        return from(values::getOrDefault);
    }

    private static SaucyLinkEmbedConfig from(BiFunction<String, String, String> get) {
        return new SaucyLinkEmbedConfig(
                Boolean.parseBoolean(get.apply("SAUCY_LINK_EMBEDS_ENABLED", String.valueOf(DEFAULT_ENABLED))),
                positiveInt(get, "SAUCY_LINK_CACHE_TTL_SECONDS", DEFAULT_CACHE_TTL_SECONDS),
                positiveInt(get, "SAUCY_MAX_LINKS_PER_MESSAGE", DEFAULT_MAX_LINKS_PER_MESSAGE),
                positiveInt(get, "SAUCY_MAX_EMBEDS_PER_MESSAGE", DEFAULT_MAX_EMBEDS_PER_MESSAGE),
                positiveLong(get, "SAUCY_MAX_FILE_BYTES", DEFAULT_MAX_FILE_BYTES),
                Boolean.parseBoolean(get.apply("SAUCY_SEND_MATCHED_MESSAGE", String.valueOf(DEFAULT_SEND_MATCHED_MESSAGE))),
                get.apply("SAUCY_MATCHED_MESSAGE", DEFAULT_MATCHED_MESSAGE),
                get.apply("SAUCY_PIXIV_SESSION_COOKIE", DEFAULT_PIXIV_SESSION_COOKIE),
                positiveInt(get, "SAUCY_PIXIV_IMAGE_LIMIT", DEFAULT_PIXIV_IMAGE_LIMIT),
                get.apply("SAUCY_PIXIV_UGOIRA_FORMAT", DEFAULT_PIXIV_UGOIRA_FORMAT),
                positiveInt(get, "SAUCY_PIXIV_UGOIRA_BITRATE", DEFAULT_PIXIV_UGOIRA_BITRATE),
                domains(get.apply("SAUCY_MISSKEY_DOMAINS", String.join(",", DEFAULT_MISSKEY_DOMAINS)))
        );
    }

    private static int positiveInt(BiFunction<String, String, String> get, String key, int defaultValue) {
        String value = get.apply(key, String.valueOf(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        logger.warn("Invalid positive integer for {}, using default {}", key, defaultValue);
        return defaultValue;
    }

    private static long positiveLong(BiFunction<String, String, String> get, String key, long defaultValue) {
        String value = get.apply(key, String.valueOf(defaultValue));
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        logger.warn("Invalid positive long for {}, using default {}", key, defaultValue);
        return defaultValue;
    }

    private static List<String> domains(String rawDomains) {
        List<String> domains = Arrays.stream(rawDomains.split(","))
                .map(String::trim)
                .filter(domain -> !domain.isBlank())
                .toList();

        if (domains.isEmpty()) {
            return DEFAULT_MISSKEY_DOMAINS;
        }

        return domains;
    }
}
