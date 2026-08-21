package org.camelia.studio.kiss.shot.acerola.services;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServer;
import org.camelia.studio.kiss.shot.acerola.repositories.DiscordServerRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class DiscordServerService {
    private static DiscordServerService instance;

    private final DiscordServerRepository repository;
    private final Clock clock;

    public static synchronized DiscordServerService getInstance() {
        if (instance == null) {
            instance = new DiscordServerService(new DiscordServerRepository(), Clock.systemUTC());
        }
        return instance;
    }

    DiscordServerService(DiscordServerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DiscordServer register(String discordId) {
        return repository.activate(discordId, Instant.now(clock));
    }

    public void markLeft(String discordId) {
        repository.markLeft(discordId, Instant.now(clock));
    }

    public void synchronize(Collection<Guild> guilds, Set<String> unavailableGuildIds) {
        repository.synchronize(
                guilds.stream().map(Guild::getId).toList(),
                unavailableGuildIds,
                Instant.now(clock));
    }

    public Optional<String> getLogChannelId(String discordId) {
        return repository.findLogChannelId(discordId);
    }
}
