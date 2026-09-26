package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServer;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServerLifecycle;
import org.camelia.studio.kiss.shot.acerola.models.ServerSynchronization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cycle de vie des serveurs et salon de logs (routes {@code /servers}).
 */
public class ServerApi {
    private final ApiClient client;

    public ServerApi(ApiClient client) {
        this.client = client;
    }

    /**
     * Aligne l'API sur les serveurs vus par le bot.
     */
    public ServerSynchronization synchronize(
            Collection<String> activeDiscordIds,
            Collection<String> unavailableDiscordIds
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activeIds", Set.copyOf(activeDiscordIds));
        body.put("unavailableIds", Set.copyOf(unavailableDiscordIds));
        JsonNode response = client.post("/servers/sync", body);

        // Une réponse sans ces champs n'est pas celle de l'API : ne pas la prendre pour un succès.
        if (!response.path("registered").isInt() || !response.path("left").isArray()) {
            throw new ApiException("Réponse de synchronisation inattendue : " + response, null);
        }
        List<String> left = new ArrayList<>();
        response.path("left").forEach(id -> left.add(id.asText()));
        return new ServerSynchronization(response.path("registered").asInt(), left);
    }

    /**
     * Arrivée du bot : crée le serveur ou le repasse {@code ACTIVE}. Idempotent.
     */
    public DiscordServer register(String discordId) {
        return toServer(client.put("/servers/" + discordId, Map.of()));
    }

    public DiscordServer markLeft(String discordId) {
        return toServer(client.post("/servers/" + discordId + "/leave", Map.of()));
    }

    public DiscordServer setLogChannel(String discordId, String channelId, String actorId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actorId", actorId);
        body.put("channelId", channelId);
        return toServer(client.put("/servers/" + discordId + "/log-channel", body));
    }

    private static DiscordServer toServer(JsonNode node) {
        return new DiscordServer(
                node.path("discordId").asText(),
                DiscordServerLifecycle.valueOf(node.path("lifecycle").asText()),
                ApiJson.nullableInstant(node.path("joinedAt")),
                ApiJson.nullableInstant(node.path("leftAt")),
                node.path("locale").asText(),
                ApiJson.nullableText(node.path("logChannelId")));
    }
}
