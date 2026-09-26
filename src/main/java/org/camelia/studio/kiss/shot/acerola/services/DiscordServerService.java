package org.camelia.studio.kiss.shot.acerola.services;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.kiss.shot.acerola.api.ApiClient;
import org.camelia.studio.kiss.shot.acerola.api.ServerApi;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServer;
import org.camelia.studio.kiss.shot.acerola.models.ServerSynchronization;

import java.util.Collection;
import java.util.Set;

public class DiscordServerService {
    private static DiscordServerService instance;

    private final ServerApi api;

    public static synchronized DiscordServerService getInstance() {
        if (instance == null) {
            instance = new DiscordServerService(new ServerApi(ApiClient.getInstance()));
        }
        return instance;
    }

    DiscordServerService(ServerApi api) {
        this.api = api;
    }

    public DiscordServer register(String discordId) {
        return api.register(discordId);
    }

    public void markLeft(String discordId) {
        api.markLeft(discordId);
    }

    public ServerSynchronization synchronize(Collection<Guild> guilds, Set<String> unavailableGuildIds) {
        return api.synchronize(guilds.stream().map(Guild::getId).toList(), unavailableGuildIds);
    }
}
