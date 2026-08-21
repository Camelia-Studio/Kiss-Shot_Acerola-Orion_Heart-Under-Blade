package org.camelia.studio.kiss.shot.acerola.repositories;

import org.camelia.studio.kiss.shot.acerola.interfaces.IEntity;
import org.camelia.studio.kiss.shot.acerola.models.Averto;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServer;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.User;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.utils.ReflectionUtils;
import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.service.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgreSqlMultiServerIntegrationTest {
    private static final String HISTORICAL_GUILD_ID = "100000000000000001";
    private static final String SECOND_GUILD_ID = "200000000000000002";

    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("kiss_shot_integration")
            .withUsername("integration")
            .withPassword("integration");

    private SessionFactory sessionFactory;

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @AfterEach
    void closeSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
            sessionFactory = null;
        }
    }

    @Test
    void migrationsApplyOnAnEmptyDatabaseAndNewServersStartDisabled() {
        Flyway flyway = migrate(Map.of());

        assertEquals("4", flyway.info().current().getVersion().getVersion());

        sessionFactory = buildValidatedSessionFactory();
        DiscordServerRepository servers = new DiscordServerRepository(sessionFactory);
        ServerConfigurationRepository configurations = new ServerConfigurationRepository(sessionFactory);

        servers.activate(SECOND_GUILD_ID, Instant.parse("2026-08-22T10:00:00Z"));

        assertEquals(ModuleType.values().length, configurations.findAll(SECOND_GUILD_ID).size());
        assertTrue(configurations.findAll(SECOND_GUILD_ID).stream()
                .allMatch(configuration -> configuration.status() == ModuleStatus.DISABLED));
    }

    @Test
    void historicalDatabaseMigratesWarningsAndLegacyConfigurationToItsGuild() throws Exception {
        executeClasspathSql("db/migration/V1__legacy_schema.sql");
        executeSql("""
                INSERT INTO "users" ("discordId", "createdAt", "updatedAt") VALUES
                    ('300000000000000003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('400000000000000004', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                INSERT INTO "avertos" ("reason", "file", "createdAt", "updatedAt", "user_id", "moderator_id")
                SELECT 'historique', 'preuve.png', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, target."id", moderator."id"
                FROM "users" target, "users" moderator
                WHERE target."discordId" = '300000000000000003'
                  AND moderator."discordId" = '400000000000000004';
                """);

        Flyway flyway = migrate(Map.of(
                "legacyGuildId", HISTORICAL_GUILD_ID,
                "legacyLogChannelId", "500000000000000005",
                "legacyDefaultRoleId", "600000000000000006",
                "legacyAutoBanChannelIds", "700000000000000007,700000000000000008",
                "legacyAutoBanRoleIds", "800000000000000008",
                "legacyProtectedRoleIds", "900000000000000009",
                "legacyNoEmbedChannelIds", "910000000000000010",
                "legacyAntiRaidMinimumAccountAgeDays", "14",
                "legacyAntiRaidMentionLimit", "8",
                "legacyAntiRaidMentionWindowSeconds", "20"));

        assertEquals("4", flyway.info().current().getVersion().getVersion());
        assertEquals(1, queryInt("""
                SELECT count(*) FROM "avertos" warning
                JOIN "discord_servers" server ON server."id" = warning."server_id"
                WHERE server."discord_id" = '100000000000000001'
                """));
        assertEquals(10, queryInt("""
                SELECT count(*) FROM "server_modules" module
                JOIN "discord_servers" server ON server."id" = module."server_id"
                WHERE server."discord_id" = '100000000000000001' AND module."status" = 'DISABLED'
                """));
        assertEquals("500000000000000005", queryString("""
                SELECT settings."log_channel_id" FROM "server_settings" settings
                JOIN "discord_servers" server ON server."id" = settings."server_id"
                WHERE server."discord_id" = '100000000000000001'
                """));
        assertEquals(1, migratedResourceCount(
                "server_module_roles", "role_id", "AUTO_ROLE", "TARGET", "600000000000000006"));
        assertEquals(2, migratedResourceCount(
                "server_module_channels", "channel_id", "AUTO_SANCTION_CHANNEL", "WATCHED",
                "700000000000000007", "700000000000000008"));
        assertEquals(1, migratedResourceCount(
                "server_module_roles", "role_id", "AUTO_SANCTION_ROLE", "WATCHED", "800000000000000008"));
        assertEquals(1, queryInt("""
                SELECT count(*) FROM "server_protected_roles" resource
                JOIN "discord_servers" server ON server."id" = resource."server_id"
                WHERE server."discord_id" = '100000000000000001'
                  AND resource."role_id" = '900000000000000009'
                """));
        assertEquals(1, migratedResourceCount(
                "server_module_channels", "channel_id", "INTEGRATION_REMOVAL", "WATCHED",
                "910000000000000010"));
        assertEquals("14", migratedSetting("ANTI_RECENT_MAX_AGE_DAYS"));
        assertEquals("8", migratedSetting("ANTI_MENTION_LIMIT"));
        assertEquals("20", migratedSetting("ANTI_MENTION_WINDOW_SECONDS"));

        sessionFactory = buildValidatedSessionFactory();
    }

    @Test
    void configurationsRolesAndChannelsRemainIsolatedBetweenTwoServers() {
        migrate(Map.of());
        sessionFactory = buildValidatedSessionFactory();
        DiscordServerRepository servers = new DiscordServerRepository(sessionFactory);
        ServerConfigurationRepository configurations = new ServerConfigurationRepository(sessionFactory);
        servers.activate(HISTORICAL_GUILD_ID, Instant.parse("2026-08-22T10:00:00Z"));
        servers.activate(SECOND_GUILD_ID, Instant.parse("2026-08-22T10:00:00Z"));

        ModuleConfiguration configuredFirst = configurations.configure(
                HISTORICAL_GUILD_ID,
                automaticChannelConfiguration(
                        "110000000000000011",
                        "120000000000000012",
                        "130000000000000013",
                        "BAN"),
                ModuleStatus.ACTIVE,
                null,
                "actor-a");
        ModuleConfiguration configuredSecond = configurations.configure(
                SECOND_GUILD_ID,
                automaticChannelConfiguration(
                        "210000000000000021",
                        "220000000000000022",
                        "230000000000000023",
                        "KICK"),
                ModuleStatus.ACTIVE,
                null,
                "actor-b");

        assertEquals("BAN", configuredFirst.settings().get(ModuleSetting.SANCTION_ACTION));
        assertEquals("KICK", configuredSecond.settings().get(ModuleSetting.SANCTION_ACTION));

        ModuleConfiguration first = configurations.find(
                HISTORICAL_GUILD_ID, ModuleType.AUTO_SANCTION_CHANNEL).orElseThrow();
        ModuleConfiguration second = configurations.find(
                SECOND_GUILD_ID, ModuleType.AUTO_SANCTION_CHANNEL).orElseThrow();

        assertEquals("110000000000000011", first.logChannelId());
        assertEquals(Set.of("120000000000000012"), first.channels(ModuleResourcePurpose.WATCHED));
        assertEquals(Set.of("130000000000000013"), first.roles(ModuleResourcePurpose.PROTECTED));
        assertEquals("BAN", first.settings().get(ModuleSetting.SANCTION_ACTION));

        assertEquals("210000000000000021", second.logChannelId());
        assertEquals(Set.of("220000000000000022"), second.channels(ModuleResourcePurpose.WATCHED));
        assertEquals(Set.of("230000000000000023"), second.roles(ModuleResourcePurpose.PROTECTED));
        assertEquals("KICK", second.settings().get(ModuleSetting.SANCTION_ACTION));
    }

    @Test
    void warningsCannotBeReadFromAnotherServer() {
        migrate(Map.of());
        sessionFactory = buildValidatedSessionFactory();
        DiscordServerRepository servers = new DiscordServerRepository(sessionFactory);
        DiscordServer firstServer = servers.activate(
                HISTORICAL_GUILD_ID, Instant.parse("2026-08-22T10:00:00Z"));
        DiscordServer secondServer = servers.activate(
                SECOND_GUILD_ID, Instant.parse("2026-08-22T10:00:00Z"));

        User warnedUser = new User("300000000000000003");
        User moderator = new User("400000000000000004");
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(warnedUser);
            session.persist(moderator);
            Averto firstWarning = new Averto(warnedUser, moderator, firstServer);
            firstWarning.setReason("serveur-a");
            session.persist(firstWarning);
            Averto secondWarning = new Averto(warnedUser, moderator, secondServer);
            secondWarning.setReason("serveur-b");
            session.persist(secondWarning);
            session.getTransaction().commit();
        }

        AvertoRepository warnings = new AvertoRepository(sessionFactory);

        assertEquals(
                Set.of("serveur-a"),
                warnings.findByServerAndUser(HISTORICAL_GUILD_ID, warnedUser.getDiscordId(), 10)
                        .stream().map(Averto::getReason).collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                Set.of("serveur-b"),
                warnings.findByServer(SECOND_GUILD_ID, 10)
                        .stream().map(Averto::getReason).collect(java.util.stream.Collectors.toSet()));
    }

    private ModuleConfiguration automaticChannelConfiguration(
            String logChannelId,
            String watchedChannelId,
            String protectedRoleId,
            String action
    ) {
        return new ModuleConfiguration(
                ModuleType.AUTO_SANCTION_CHANNEL,
                ModuleStatus.DISABLED,
                null,
                logChannelId,
                Map.of(ModuleResourcePurpose.PROTECTED, Set.of(protectedRoleId)),
                Map.of(ModuleResourcePurpose.WATCHED, Set.of(watchedChannelId)),
                Map.of(
                        ModuleSetting.SANCTION_ACTION, action,
                        ModuleSetting.SANCTION_TIMEOUT_SECONDS, "600",
                        ModuleSetting.SANCTION_BAN_HISTORY_DAYS, "0",
                        ModuleSetting.SANCTION_DELETE_MESSAGE, "false"));
    }

    private Flyway migrate(Map<String, String> overrides) {
        Map<String, String> placeholders = new HashMap<>(Map.of(
                "legacyGuildId", "",
                "legacyLogChannelId", "",
                "legacyDefaultRoleId", "",
                "legacyAutoBanChannelIds", "",
                "legacyAutoBanRoleIds", "",
                "legacyProtectedRoleIds", "",
                "legacyNoEmbedChannelIds", "",
                "legacyAntiRaidMinimumAccountAgeDays", "7",
                "legacyAntiRaidMentionLimit", "5",
                "legacyAntiRaidMentionWindowSeconds", "10"));
        placeholders.putAll(overrides);

        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .placeholders(placeholders)
                .load();
        flyway.migrate();
        return flyway;
    }

    private SessionFactory buildValidatedSessionFactory() {
        Configuration configuration = new Configuration();
        configuration.setProperty(Environment.HBM2DDL_AUTO, "validate");
        configuration.setProperty("jakarta.persistence.jdbc.url", postgres.getJdbcUrl());
        configuration.setProperty("jakarta.persistence.jdbc.user", postgres.getUsername());
        configuration.setProperty("jakarta.persistence.jdbc.password", postgres.getPassword());
        configuration.setProperty(Environment.GLOBALLY_QUOTED_IDENTIFIERS, "true");
        configuration.setProperty("hibernate.jdbc.time_zone", "UTC");

        for (IEntity entity : ReflectionUtils.loadClasses(
                "org.camelia.studio.kiss.shot.acerola.models", IEntity.class)) {
            configuration.addAnnotatedClass(entity.getClass());
        }

        ServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties())
                .build();
        return configuration.buildSessionFactory(registry);
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void executeClasspathSql(String resource) throws IOException, SQLException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Ressource SQL introuvable : " + resource);
            }
            executeSql(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void executeSql(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private int migratedResourceCount(
            String table,
            String column,
            String module,
            String purpose,
            String... values
    ) throws SQLException {
        String expected = String.join("','", values);
        return queryInt("""
                SELECT count(*) FROM "%s" resource
                JOIN "server_modules" module ON module."id" = resource."server_module_id"
                JOIN "discord_servers" server ON server."id" = module."server_id"
                WHERE server."discord_id" = '%s' AND module."module" = '%s'
                  AND resource."purpose" = '%s' AND resource."%s" IN ('%s')
                """.formatted(table, HISTORICAL_GUILD_ID, module, purpose, column, expected));
    }

    private String migratedSetting(String setting) throws SQLException {
        return queryString("""
                SELECT value."value" FROM "server_module_settings" value
                JOIN "server_modules" module ON module."id" = value."server_module_id"
                JOIN "discord_servers" server ON server."id" = module."server_id"
                WHERE server."discord_id" = '%s' AND module."module" = 'ANTI_RAID'
                  AND value."setting" = '%s'
                """.formatted(HISTORICAL_GUILD_ID, setting));
    }
}
