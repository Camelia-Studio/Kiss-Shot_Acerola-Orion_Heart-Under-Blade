package org.camelia.studio.kiss.shot.acerola.services.configuration;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Retrouve un salon de logs, y compris un fil archivé : JDA retire les fils archivés de son cache
 * et n'offre pas de recherche par identifiant. Le fil est alors cherché parmi les fils archivés du serveur ;
 * Discord le rouvre de lui-même dès que le bot y publie (s'il n'est pas verrouillé).
 */
public class LogChannelResolver {
    private static final Logger logger = LoggerFactory.getLogger(LogChannelResolver.class);
    // Une recherche (réussie ou non) est réutilisée un moment : un salon supprimé ne relance pas
    // une recherche dans tout le serveur à chaque log.
    private static final Duration LOOKUP_TTL = Duration.ofMinutes(5);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(15);
    private static final int ARCHIVED_THREADS_PER_CHANNEL = 100;

    private static LogChannelResolver instance;

    private final Map<String, Lookup> lookups = new ConcurrentHashMap<>();
    private final Clock clock;

    LogChannelResolver(Clock clock) {
        this.clock = clock;
    }

    public static synchronized LogChannelResolver getInstance() {
        if (instance == null) {
            instance = new LogChannelResolver(Clock.systemUTC());
        }
        return instance;
    }

    public CompletableFuture<Optional<GuildMessageChannel>> resolve(Guild guild, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        GuildMessageChannel cached = guild.getChannelById(GuildMessageChannel.class, channelId);
        if (cached != null) {
            lookups.remove(channelId);
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        Instant now = clock.instant();
        Lookup lookup = lookups.compute(channelId, (id, current) ->
                current != null && current.startedAt().plus(LOOKUP_TTL).isAfter(now)
                        ? current
                        : new Lookup(now, findArchivedThread(guild, id)));
        return lookup.result().thenApply(thread -> thread.map(GuildMessageChannel.class::cast));
    }

    /**
     * Version bloquante pour la validation ; une recherche qui n'aboutit pas vaut « introuvable ».
     */
    public Optional<GuildMessageChannel> resolveNow(Guild guild, String channelId) {
        try {
            return resolve(guild, channelId).get(LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException exception) {
            logger.warn("Recherche du salon de logs {} impossible sur {}", channelId, guild.getId(), exception);
            return Optional.empty();
        }
    }

    private CompletableFuture<Optional<ThreadChannel>> findArchivedThread(Guild guild, String channelId) {
        Member self = guild.getSelfMember();
        List<CompletableFuture<List<ThreadChannel>>> searches = new ArrayList<>();

        for (GuildChannel channel : guild.getChannelCache()) {
            if (!(channel instanceof IThreadContainer container)
                    || !self.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)) {
                continue;
            }
            searches.add(search(container::retrieveArchivedPublicThreadChannels));
            if (channel.getType() == ChannelType.TEXT) {
                searches.add(search(self.hasPermission(channel, Permission.MANAGE_THREADS)
                        ? container::retrieveArchivedPrivateThreadChannels
                        : container::retrieveArchivedPrivateJoinedThreadChannels));
            }
        }

        return CompletableFuture.allOf(searches.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> searches.stream()
                        .flatMap(search -> search.join().stream())
                        .filter(thread -> thread.getId().equals(channelId))
                        .findFirst());
    }

    private CompletableFuture<List<ThreadChannel>> search(Supplier<ThreadChannelPaginationAction> action) {
        try {
            return action.get()
                    .takeAsync(ARCHIVED_THREADS_PER_CHANNEL)
                    .exceptionally(error -> {
                        logger.debug("Fils archivés illisibles", error);
                        return List.of();
                    });
        } catch (RuntimeException exception) {
            // Permission manquante ou type de salon sans fils privés : rien à chercher ici.
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private record Lookup(Instant startedAt, CompletableFuture<Optional<ThreadChannel>> result) {
    }
}
