package org.camelia.studio.kiss.shot.acerola.services.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.kiss.shot.acerola.api.FakeApiServer;
import org.camelia.studio.kiss.shot.acerola.api.ModuleApi;
import org.camelia.studio.kiss.shot.acerola.api.ServerApi;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleConfigurationServiceTest {
    private static final String GUILD_ID = "123456789012345678";
    private static final String MODULE_PATH = "/servers/" + GUILD_ID + "/modules/ANTI_RAID";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Guild guild = (Guild) Proxy.newProxyInstance(
            Guild.class.getClassLoader(),
            new Class<?>[]{Guild.class},
            (proxy, method, arguments) -> method.getName().equals("getId") ? GUILD_ID : null);
    private final StubValidator validator = new StubValidator();
    private final MutableClock clock = new MutableClock();
    private FakeApiServer server;
    private ModuleConfigurationService service;

    @BeforeEach
    void startServer() throws IOException {
        server = new FakeApiServer();
        service = new ModuleConfigurationService(
                new ModuleApi(server.client()),
                new ServerApi(server.client()),
                validator,
                clock);
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void activationSendsOnlyTheRequestedChangesOnceDiscordValidationPasses() throws IOException {
        server.respond("GET", MODULE_PATH, 200, module("DISABLED", null));
        server.respond("PATCH", MODULE_PATH, 200, module("ACTIVE", null));
        ModuleConfigurationSettings settings = new ModuleConfigurationSettings(
                "111",
                Map.of(ModuleResourcePurpose.PROTECTED, Set.of("222")),
                Map.of());

        ConfigurationOperationResult result = service.activate(guild, ModuleType.ANTI_RAID, "42", settings);

        assertTrue(result.success());
        assertEquals(mapper.readTree("""
                {"actorId":"42","status":"ACTIVE","logChannelId":"111","roles":{"PROTECTED":["222"]}}"""),
                mapper.readTree(server.requests("PATCH").getFirst().body()));
    }

    @Test
    void activationRefusedByDiscordValidationNeverReachesTheApi() {
        server.respond("GET", MODULE_PATH, 200, module("DISABLED", null));
        validator.result = ModuleValidationResult.invalid("Permission KICK_MEMBERS manquante");

        ConfigurationOperationResult result = service.activate(guild, ModuleType.ANTI_RAID, "42");

        assertFalse(result.success());
        assertEquals("Activation refusée : Permission KICK_MEMBERS manquante.", result.message());
        assertTrue(server.requests("PATCH").isEmpty());
    }

    @Test
    void activationRulesRejectedByTheApiBecomeAFailureMessage() {
        server.respond("GET", MODULE_PATH, 200, module("DISABLED", null));
        server.respond("PATCH", MODULE_PATH, 422, """
                {"error":{"status":422,"message":"Requête invalide.",
                "violations":{"status":"Un salon de logs est requis."}}}""");

        ConfigurationOperationResult result = service.activate(guild, ModuleType.ANTI_RAID, "42");

        assertFalse(result.success());
        assertEquals("Activation refusée : Un salon de logs est requis.", result.message());
    }

    @Test
    void configuringAnActiveModuleThatBecomesInvalidSuspendsIt() throws IOException {
        server.respond("GET", MODULE_PATH, 200, module("ACTIVE", null));
        server.respond("PATCH", MODULE_PATH, 200, module("SUSPENDED", "Salon supprimé"));
        validator.result = ModuleValidationResult.invalid("Salon supprimé");

        ConfigurationOperationResult result = service.configure(
                guild, ModuleType.ANTI_RAID, "42", ModuleConfigurationSettings.empty());

        assertTrue(result.success());
        assertEquals("Les réglages sont enregistrés. Le module a été suspendu : Salon supprimé.", result.message());
        assertEquals(mapper.readTree("""
                {"actorId":"42","status":"SUSPENDED","suspensionReason":"Salon supprimé"}"""),
                mapper.readTree(server.requests("PATCH").getFirst().body()));
    }

    @Test
    void configuringAValidModuleKeepsItsStatusOutOfTheRequest() throws IOException {
        server.respond("GET", MODULE_PATH, 200, module("ACTIVE", null));
        server.respond("PATCH", MODULE_PATH, 200, module("ACTIVE", null));

        ConfigurationOperationResult result = service.configure(
                guild,
                ModuleType.ANTI_RAID,
                "42",
                new ModuleConfigurationSettings(
                        null, Map.of(), Map.of(), Map.of(ModuleSetting.ANTI_MENTION_LIMIT, "8")));

        assertEquals("Les réglages sont enregistrés. Le module reste actif.", result.message());
        assertEquals(mapper.readTree("""
                {"actorId":"42","settings":{"ANTI_MENTION_LIMIT":"8"}}"""),
                mapper.readTree(server.requests("PATCH").getFirst().body()));
    }

    @Test
    void anActiveModuleFailingDiscordValidationIsSuspendedByTheSystem() throws IOException {
        server.respond("GET", MODULE_PATH, 200, module("ACTIVE", null));
        server.respond("PATCH", MODULE_PATH, 200, module("SUSPENDED", "Permission manquante"));
        validator.result = ModuleValidationResult.invalid("Permission manquante");

        ModuleAccessResult access = service.checkAccess(guild, ModuleType.ANTI_RAID);

        assertFalse(access.allowed());
        assertTrue(access.message().startsWith("Ce module est suspendu : Permission manquante."));
        assertEquals(mapper.readTree("""
                {"actorId":"SYSTEM_VALIDATION","status":"SUSPENDED","suspensionReason":"Permission manquante"}"""),
                mapper.readTree(server.requests("PATCH").getFirst().body()));
    }

    @Test
    void resolvedModulesAreCachedBrieflyThenReloadedFromTheApi() {
        server.respond("GET", MODULE_PATH, 200, module("ACTIVE", null));

        service.checkAccess(guild, ModuleType.ANTI_RAID);
        service.checkAccess(guild, ModuleType.ANTI_RAID);
        assertEquals(1, server.requests("GET").size());

        clock.advance(Duration.ofSeconds(31));
        service.checkAccess(guild, ModuleType.ANTI_RAID);

        assertEquals(2, server.requests("GET").size());
    }

    @Test
    void evictedGuildsAreReloadedImmediately() {
        server.respond("GET", MODULE_PATH, 200, module("ACTIVE", null));
        service.checkAccess(guild, ModuleType.ANTI_RAID);

        service.evictGuild(GUILD_ID);
        service.checkAccess(guild, ModuleType.ANTI_RAID);

        assertEquals(2, server.requests("GET").size());
    }

    @Test
    void disablingAModuleSendsTheDisabledStatus() throws IOException {
        server.respond("GET", MODULE_PATH, 200, module("ACTIVE", null));
        server.respond("PATCH", MODULE_PATH, 200, module("DISABLED", null));

        ConfigurationOperationResult result = service.disable(guild, ModuleType.ANTI_RAID, "42");

        assertTrue(result.success());
        assertEquals(mapper.readTree("{\"actorId\":\"42\",\"status\":\"DISABLED\"}"),
                mapper.readTree(server.requests("PATCH").getFirst().body()));
        assertEquals(ModuleStatus.DISABLED, service.checkAccess(guild, ModuleType.ANTI_RAID).configuration().status());
    }

    @Test
    void anUnknownServerMakesTheModuleUnavailable() {
        ModuleAccessResult access = service.checkAccess(guild, ModuleType.ANTI_RAID);

        assertFalse(access.allowed());
        assertEquals("La configuration de ce serveur est temporairement indisponible.", access.message());
    }

    @Test
    void anApiOutageMakesTheModuleUnavailable() {
        server.respond("GET", MODULE_PATH, 500, "{\"error\":{\"status\":500,\"message\":\"Erreur interne.\"}}");

        ModuleAccessResult access = service.checkAccess(guild, ModuleType.ANTI_RAID);

        assertFalse(access.allowed());
        assertEquals("La configuration de ce serveur est temporairement indisponible.", access.message());
    }

    @Test
    void changingTheLogChannelRevalidatesActiveModules() {
        server.respond("PUT", "/servers/" + GUILD_ID + "/log-channel", 200, """
                {"discordId":"123456789012345678","lifecycle":"ACTIVE","joinedAt":"2026-01-01T10:00:00+00:00",
                 "leftAt":null,"locale":"fr","logChannelId":null}""");
        server.respond("GET", "/servers/" + GUILD_ID + "/modules", 200, "[" + module("ACTIVE", null) + "]");

        ConfigurationOperationResult result = service.setLogChannel(guild, null, "42");

        assertTrue(result.success());
        assertEquals(1, server.requests("PUT").size());
        assertEquals(1, server.requests("GET").size());
    }

    private static String module(String status, String suspensionReason) {
        return """
                {"module":"ANTI_RAID","status":"%s","suspensionReason":%s,"logChannelId":"111",
                 "roles":{"PROTECTED":["222"]},"channels":{},"settings":{"ANTI_MENTION_LIMIT":"5"}}"""
                .formatted(status, suspensionReason == null ? "null" : "\"" + suspensionReason + "\"");
    }

    private static class StubValidator extends ModuleConfigurationValidator {
        private ModuleValidationResult result = ModuleValidationResult.success();

        @Override
        public ModuleValidationResult validate(Guild guild, ModuleConfiguration configuration) {
            return result;
        }

        @Override
        public ModuleValidationResult validateLogChannel(Guild guild, String channelId) {
            return ModuleValidationResult.success();
        }
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-24T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
