package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.camelia.studio.kiss.shot.acerola.models.Averto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Avertissements d'un serveur (routes {@code /servers/{id}/avertos}).
 */
public class AvertoApi {
    private final ApiClient client;

    public AvertoApi(ApiClient client) {
        this.client = client;
    }

    /**
     * Les {@code limit} avertissements les plus récents, éventuellement filtrés sur un utilisateur.
     */
    public List<Averto> findLatest(String serverDiscordId, String userDiscordId, int limit) {
        String path = "/servers/" + serverDiscordId + "/avertos?limit=" + limit;
        if (userDiscordId != null) {
            path += "&userId=" + URLEncoder.encode(userDiscordId, StandardCharsets.UTF_8);
        }

        List<Averto> avertos = new ArrayList<>();
        client.get(path).path("items").forEach(item -> avertos.add(toAverto(item)));
        return avertos;
    }

    public Averto save(String serverDiscordId, String userDiscordId, String moderatorDiscordId, String reason, String file) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userDiscordId);
        body.put("moderatorId", moderatorDiscordId);
        body.put("reason", reason);
        body.put("file", file);
        return toAverto(client.post("/servers/" + serverDiscordId + "/avertos", body));
    }

    private static Averto toAverto(JsonNode node) {
        return new Averto(
                node.path("id").asLong(),
                node.path("userId").asText(),
                node.path("moderatorId").asText(),
                ApiJson.nullableText(node.path("reason")),
                ApiJson.nullableText(node.path("file")),
                ApiJson.nullableInstant(node.path("createdAt")));
    }
}
