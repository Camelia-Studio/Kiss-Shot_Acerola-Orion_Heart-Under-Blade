package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientTest {
    @Test
    void everyRequestCarriesTheBotToken() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("GET", "/servers/1", 200, "{\"discordId\":\"1\"}");

            JsonNode response = server.client().get("/servers/1");

            assertEquals("1", response.path("discordId").asText());
            assertEquals("Bearer " + FakeApiServer.TOKEN, server.requests().getFirst().authorization());
        }
    }

    @Test
    void bodiesAreSentAsJsonWithExplicitNulls() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PUT", "/servers/1/log-channel", 200, "{}");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("actorId", "42");
            body.put("channelId", null);

            server.client().put("/servers/1/log-channel", body);

            assertEquals("{\"actorId\":\"42\",\"channelId\":null}", server.requests().getFirst().body());
        }
    }

    @Test
    void trailingSlashOfTheBaseUrlIsIgnored() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("GET", "/servers/1", 200, "{}");
            ApiClient client = new ApiClient(
                    HttpClient.newHttpClient(), new ObjectMapper(), server.baseUrl() + "/", FakeApiServer.TOKEN);

            client.get("/servers/1");

            assertEquals("/servers/1", server.requests().getFirst().path());
        }
    }

    @Test
    void invalidRequestsExposeTheirViolations() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("PATCH", "/servers/1/modules/ANTI_RAID", 422, """
                    {"error":{"status":422,"message":"Requête invalide.",
                    "violations":{"status":"Un salon de logs est requis."}}}""");

            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> server.client().patch("/servers/1/modules/ANTI_RAID", Map.of("actorId", "42")));

            assertTrue(exception.isInvalidRequest());
            assertEquals("Requête invalide.", exception.getMessage());
            assertEquals(Map.of("status", "Un salon de logs est requis."), exception.violations());
            assertEquals("Un salon de logs est requis.", exception.describe());
        }
    }

    @Test
    void errorsWithoutJsonBodyKeepTheirStatus() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.respond("GET", "/servers/1", 502, "<html>Bad gateway</html>");

            ApiException exception = assertThrows(ApiException.class, () -> server.client().get("/servers/1"));

            assertEquals(502, exception.status());
            assertFalse(exception.isNotFound());
        }
    }

    @Test
    void aRedirectIsAnErrorRatherThanASilentSuccess() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            server.redirect("POST", "/servers/sync", 301, "https://bot.example.org/api/servers/sync");

            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> server.client().post("/servers/sync", Map.of("activeIds", List.of())));

            assertEquals(301, exception.status());
            assertTrue(exception.getMessage().contains("https://bot.example.org/api/servers/sync"));
            assertEquals(1, server.requests().size());
        }
    }

    @Test
    void notFoundBecomesEmptyWhenTheResourceIsOptional() throws IOException {
        try (FakeApiServer server = new FakeApiServer()) {
            Optional<JsonNode> response = server.client().getIfExists("/servers/404");

            assertTrue(response.isEmpty());
        }
    }

    @Test
    void anUnreachableApiRaisesAnApiException() throws IOException {
        ApiClient client;
        try (FakeApiServer server = new FakeApiServer()) {
            client = server.client();
        }

        ApiException exception = assertThrows(ApiException.class, () -> client.get("/servers/1"));

        assertEquals(ApiException.UNREACHABLE, exception.status());
    }
}
