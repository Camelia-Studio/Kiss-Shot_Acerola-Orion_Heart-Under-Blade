package org.camelia.studio.kiss.shot.acerola.db;

import io.github.cdimascio.dotenv.Dotenv;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class DatabaseMigrator {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrator.class);
    private static boolean migrated;

    private DatabaseMigrator() {
    }

    public static synchronized void migrate() {
        if (migrated) {
            return;
        }

        Dotenv dotenv = Configuration.getInstance().getDotenv();
        String legacyGuildId = optionalSnowflake(dotenv, "GUILD_ID");
        String legacyLogChannelId = optionalSnowflake(dotenv, "LOG_CHANNEL_ID");
        String legacyDefaultRoleId = optionalSnowflake(dotenv, "DEFAULT_ROLE_ID");
        String legacyAutoBanChannelIds = optionalSnowflakeList(dotenv, "AUTO_BAN_CHANNEL_IDS");
        String legacyAutoBanRoleIds = optionalSnowflakeList(dotenv, "AUTO_BAN_ROLE_IDS");
        String legacyProtectedRoleIds = optionalSnowflakeList(dotenv, "AUTO_BAN_EXEMPT_ROLE_IDS");
        String legacyNoEmbedChannelIds = optionalSnowflakeList(dotenv, "NO_EMBED_CHANNEL_IDS");

        logger.info("Applying database migrations");
        Flyway.configure()
                .dataSource(
                        required(dotenv, "DB_URL"),
                        required(dotenv, "DB_USER"),
                        required(dotenv, "DB_PASSWORD"))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .placeholders(Map.of(
                        "legacyGuildId", legacyGuildId,
                        "legacyLogChannelId", legacyLogChannelId,
                        "legacyDefaultRoleId", legacyDefaultRoleId,
                        "legacyAutoBanChannelIds", legacyAutoBanChannelIds,
                        "legacyAutoBanRoleIds", legacyAutoBanRoleIds,
                        "legacyProtectedRoleIds", legacyProtectedRoleIds,
                        "legacyNoEmbedChannelIds", legacyNoEmbedChannelIds))
                .load()
                .migrate();
        migrated = true;
        logger.info("Database migrations applied successfully");
    }

    private static String required(Dotenv dotenv, String name) {
        String value = dotenv.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("La variable " + name + " est obligatoire");
        }
        return value;
    }

    private static String optionalSnowflake(Dotenv dotenv, String name) {
        String value = dotenv.get(name, "").trim();
        if (!value.isEmpty() && (!value.matches("\\d+") || value.length() > 20)) {
            throw new IllegalStateException("La variable " + name + " doit contenir un identifiant Discord valide");
        }
        return value;
    }

    private static String optionalSnowflakeList(Dotenv dotenv, String name) {
        String value = dotenv.get(name, "").trim();
        if (value.isEmpty()) {
            return "";
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .peek(item -> {
                    if (!item.matches("\\d{1,20}")) {
                        throw new IllegalArgumentException(name + " must contain only Discord snowflakes");
                    }
                })
                .collect(Collectors.joining(","));
    }
}
