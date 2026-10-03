package org.camelia.studio.kiss.shot.acerola.services;

import java.util.List;

import org.camelia.studio.kiss.shot.acerola.api.ApiClient;
import org.camelia.studio.kiss.shot.acerola.api.AvertoApi;
import org.camelia.studio.kiss.shot.acerola.models.Averto;

public class AvertoService {
    private static AvertoService instance;

    private final AvertoApi api;

    public static synchronized AvertoService getInstance() {
        if (instance == null) {
            instance = new AvertoService(new AvertoApi(ApiClient.getInstance()));
        }

        return instance;
    }

    AvertoService(AvertoApi api) {
        this.api = api;
    }

    public List<Averto> getLatestAvertos(String serverDiscordId, int amount) {
        return api.findLatest(serverDiscordId, null, amount);
    }

    public List<Averto> getLatestAvertosForUser(String serverDiscordId, String userDiscordId, int amount) {
        return api.findLatest(serverDiscordId, userDiscordId, amount);
    }

    /**
     * Enregistre l'avertissement ; l'API crée elle-même les identités de l'utilisateur et du modérateur.
     */
    public Averto save(String serverDiscordId, String userDiscordId, String moderatorDiscordId, String reason, String file) {
        return api.save(serverDiscordId, userDiscordId, moderatorDiscordId, reason, file);
    }
}
