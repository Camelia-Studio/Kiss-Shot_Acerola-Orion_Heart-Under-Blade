package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleApiTest {
    private static final String SERVER = "123456789012345678";
    private static final String ANTI_RAID = """
            {"module":"ANTI_RAID","status":"ACTIVE","suspensionReason":null,
             "logChannelId":"111111111111111111",
             "roles":{"PROTECTED":["222222222222222222","333333333333333333"]},
             "channels":{},
             "settings":{"ANTI_RECENT_ENABLED":"true","ANTI_MENTION_LIMIT":"5"}}""";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void modulesAreReadWithTheirResourcesAndSettings() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("GET", "/servers/" + SERVER + "/modules/ANTI_RAID", 200, ANTI_RAID);

            ModuleConfiguration configuration = new ModuleApi(server.client())
                    .find(SERVER, ModuleType.ANTI_RAID)
                    .orElseThrow();

            assertEquals(ModuleType.ANTI_RAID, configuration.module());
            assertEquals(ModuleStatus.ACTIVE, configuration.status());
            assertNull(configuration.suspensionReason());
            assertEquals("111111111111111111", configuration.logChannelId());
            assertEquals(
                    Set.of("222222222222222222", "333333333333333333"),
                    configuration.roles(ModuleResourcePurpose.PROTECTED));
            assertEquals(Map.of(
                    ModuleSetting.ANTI_RECENT_ENABLED, "true",
                    ModuleSetting.ANTI_MENTION_LIMIT, "5"), configuration.settings());
        }
    }

    @Test
    void unknownModulesOrServersAreEmpty() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            Optional<ModuleConfiguration> configuration = new ModuleApi(server.client())
                    .find(SERVER, ModuleType.MUSIC);

            assertTrue(configuration.isEmpty());
        }
    }

    @Test
    void allModulesAreReadInOnePass() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("GET", "/servers/" + SERVER + "/modules", 200, "["
                    + "{\"module\":\"WARNINGS\",\"status\":\"DISABLED\",\"suspensionReason\":null,"
                    + "\"logChannelId\":null,\"roles\":{},\"channels\":{},\"settings\":{}}," + ANTI_RAID + "]");

            List<ModuleConfiguration> configurations = new ModuleApi(server.client()).findAll(SERVER);

            assertEquals(
                    List.of(ModuleType.WARNINGS, ModuleType.ANTI_RAID),
                    configurations.stream().map(ModuleConfiguration::module).toList());
            assertEquals(1, server.requests().size());
        }
    }

    @Test
    void configureSendsOnlyTheChangesAndTheStatus() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PATCH", "/servers/" + SERVER + "/modules/ANTI_RAID", 200, ANTI_RAID);
            ModuleConfigurationSettings changes = new ModuleConfigurationSettings(
                    "111111111111111111",
                    Map.of(ModuleResourcePurpose.PROTECTED, Set.of("222222222222222222")),
                    Map.of(),
                    Map.of(ModuleSetting.ANTI_MENTION_LIMIT, "8"));

            new ModuleApi(server.client())
                    .configure(SERVER, ModuleType.ANTI_RAID, changes, ModuleStatus.ACTIVE, null, "444444444444444444");

            assertEquals(mapper.readTree("""
                    {"actorId":"444444444444444444","status":"ACTIVE",
                     "logChannelId":"111111111111111111",
                     "roles":{"PROTECTED":["222222222222222222"]},
                     "settings":{"ANTI_MENTION_LIMIT":"8"}}"""), body(server));
        }
    }

    @Test
    void emptyChangesLeaveTheStatusAndEverythingElseOutOfTheRequest() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PATCH", "/servers/" + SERVER + "/modules/ANTI_RAID", 200, ANTI_RAID);

            new ModuleApi(server.client())
                    .configure(SERVER, ModuleType.ANTI_RAID, ModuleConfigurationSettings.empty(), null, null, "42");

            assertEquals(mapper.readTree("{\"actorId\":\"42\"}"), body(server));
        }
    }

    @Test
    void anEmptyResourceListIsSentToClearThePurpose() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PATCH", "/servers/" + SERVER + "/modules/ANTI_RAID", 200, ANTI_RAID);
            ModuleConfigurationSettings changes = new ModuleConfigurationSettings(
                    null,
                    Map.of(ModuleResourcePurpose.PROTECTED, Set.of()),
                    Map.of());

            new ModuleApi(server.client())
                    .configure(SERVER, ModuleType.ANTI_RAID, changes, null, null, "42");

            assertEquals(mapper.readTree("{\"actorId\":\"42\",\"roles\":{\"PROTECTED\":[]}}"), body(server));
        }
    }

    @Test
    void suspensionCarriesItsReasonAndOtherStatusesDoNot() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PATCH", "/servers/" + SERVER + "/modules/ANTI_RAID", 200, ANTI_RAID);
            ModuleApi api = new ModuleApi(server.client());

            api.changeStatus(SERVER, ModuleType.ANTI_RAID, ModuleStatus.SUSPENDED, "Permission manquante", "SYSTEM_VALIDATION");
            api.changeStatus(SERVER, ModuleType.ANTI_RAID, ModuleStatus.DISABLED, "ignorée", "42");

            assertEquals(mapper.readTree("""
                    {"actorId":"SYSTEM_VALIDATION","status":"SUSPENDED","suspensionReason":"Permission manquante"}"""),
                    mapper.readTree(server.requests("PATCH").get(0).body()));
            assertEquals(mapper.readTree("{\"actorId\":\"42\",\"status\":\"DISABLED\"}"),
                    mapper.readTree(server.requests("PATCH").get(1).body()));
        }
    }

    @Test
    void aTooLongSuspensionReasonIsTruncatedToWhatTheApiAccepts() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PATCH", "/servers/" + SERVER + "/modules/ANTI_RAID", 200, ANTI_RAID);

            new ModuleApi(server.client())
                    .changeStatus(SERVER, ModuleType.ANTI_RAID, ModuleStatus.SUSPENDED, "x".repeat(300), "42");

            assertEquals(255, body(server).path("suspensionReason").asText().length());
        }
    }

    private JsonNode body(FakeApiServer server) throws IOException {
        return mapper.readTree(server.requests("PATCH").getFirst().body());
    }
}
