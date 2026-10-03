package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.models.Averto;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServer;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServerLifecycle;
import org.camelia.studio.kiss.shot.acerola.models.ServerSynchronization;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerAndAvertoApiTest {
    private static final String SERVER = "123456789012345678";
    private static final String SERVER_JSON = """
            {"discordId":"123456789012345678","lifecycle":"ACTIVE","joinedAt":"2026-01-01T10:00:00+00:00",
             "leftAt":null,"locale":"fr","logChannelId":"111111111111111111"}""";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registeringAServerReturnsItsLifecycleAndLogChannel() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PUT", "/servers/" + SERVER, 200, SERVER_JSON);

            DiscordServer registered = new ServerApi(server.client()).register(SERVER);

            assertEquals(new DiscordServer(
                    SERVER,
                    DiscordServerLifecycle.ACTIVE,
                    Instant.parse("2026-01-01T10:00:00Z"),
                    null,
                    "fr",
                    "111111111111111111"), registered);
        }
    }

    @Test
    void leavingAServerPostsToItsLeaveRoute() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("POST", "/servers/" + SERVER + "/leave", 200, SERVER_JSON.replace("ACTIVE", "LEFT"));

            DiscordServer left = new ServerApi(server.client()).markLeft(SERVER);

            assertEquals(DiscordServerLifecycle.LEFT, left.lifecycle());
        }
    }

    @Test
    void synchronizationSendsActiveAndUnavailableServers() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("POST", "/servers/sync", 200, "{\"registered\":1,\"left\":[\"888888888888888888\"]}");

            ServerSynchronization result = new ServerApi(server.client())
                    .synchronize(List.of(SERVER), List.of("999999999999999999"));

            JsonNode body = mapper.readTree(server.requests().getFirst().body());
            assertEquals(SERVER, body.path("activeIds").get(0).asText());
            assertEquals("999999999999999999", body.path("unavailableIds").get(0).asText());
            assertEquals(new ServerSynchronization(1, List.of("888888888888888888")), result);
        }
    }

    @Test
    void aSynchronizationResponseThatIsNotTheApisIsRejected() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("POST", "/servers/sync", 200, "<html>Bienvenue</html>");

            assertThrows(ApiException.class, () -> new ServerApi(server.client()).synchronize(List.of(SERVER), List.of()));
        }
    }

    @Test
    void clearingTheLogChannelSendsAnExplicitNull() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PUT", "/servers/" + SERVER + "/log-channel", 200, SERVER_JSON);

            new ServerApi(server.client()).setLogChannel(SERVER, null, "42");

            assertEquals(
                    mapper.readTree("{\"actorId\":\"42\",\"channelId\":null}"),
                    mapper.readTree(server.requests().getFirst().body()));
        }
    }

    @Test
    void warningsAreListedNewestFirstAndFilteredByUser() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("GET", "/servers/" + SERVER + "/avertos?limit=10&userId=666", 200, """
                    {"items":[{"id":3,"userId":"666","moderatorId":"444","reason":"Insultes","file":null,
                    "createdAt":"2026-09-24T11:23:54+00:00"}],"total":3,"page":1,"limit":10}""");

            List<Averto> avertos = new AvertoApi(server.client()).findLatest(SERVER, "666", 10);

            assertEquals(List.of(new Averto(
                    3, "666", "444", "Insultes", null, Instant.parse("2026-09-24T11:23:54Z"))), avertos);
            assertNull(avertos.getFirst().file());
        }
    }

    @Test
    void warningsWithoutUserFilterListTheWholeServer() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("GET", "/servers/" + SERVER + "/avertos?limit=5", 200,
                    "{\"items\":[],\"total\":0,\"page\":1,\"limit\":5}");

            assertEquals(List.of(), new AvertoApi(server.client()).findLatest(SERVER, null, 5));
        }
    }

    @Test
    void savingAWarningPostsBothIdentitiesAndTheProof() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("POST", "/servers/" + SERVER + "/avertos", 201, """
                    {"id":4,"userId":"666","moderatorId":"444","reason":"Spam","file":"https://cdn/x.png",
                    "createdAt":"2026-09-24T11:23:54+00:00"}""");

            Averto saved = new AvertoApi(server.client()).save(SERVER, "666", "444", "Spam", "https://cdn/x.png");

            assertEquals(4, saved.id());
            assertEquals(
                    mapper.readTree("""
                            {"userId":"666","moderatorId":"444","reason":"Spam","file":"https://cdn/x.png"}"""),
                    mapper.readTree(server.requests().getFirst().body()));
        }
    }
}
