package org.camelia.studio.kiss.shot.acerola.listeners.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.models.ServerSynchronization;
import org.camelia.studio.kiss.shot.acerola.services.DiscordServerService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReadyListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_SECONDS = 30;

    private final ScheduledExecutorService retries = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "server-synchronization-retry");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Connecté en tant que {}", event.getJDA().getSelfUser().getAsTag());
        synchronize(event.getJDA(), 1);
    }

    /**
     * {@code getGuilds()} ne contient pas forcément tous les serveurs quand {@link ReadyEvent} est émis (serveur
     * encore en chargement côté JDA) : la synchronisation ne suffit donc pas. Chaque serveur est aussi enregistré
     * dès qu'il est prêt ; l'appel est idempotent côté API.
     */
    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        register(event.getGuild().getId());
    }

    @Override
    public void onGuildAvailable(@NotNull GuildAvailableEvent event) {
        register(event.getGuild().getId());
    }

    private void register(String guildId) {
        try {
            DiscordServerService.getInstance().register(guildId);
            logger.info("Serveur {} enregistré", guildId);
        } catch (RuntimeException exception) {
            logger.error("Impossible d'enregistrer le serveur {}", guildId, exception);
        }
    }

    /**
     * Rattrape les serveurs rejoints ou quittés pendant l'arrêt du bot. Sans cette synchronisation, un serveur
     * inconnu de l'API n'a aucune configuration : l'API est indisponible au démarrage plus souvent qu'une base
     * locale, d'où les nouvelles tentatives.
     */
    private void synchronize(JDA jda, int attempt) {
        try {
            logger.info(
                    "Synchronisation des serveurs : {} présent(s) et {} indisponible(s) côté Discord",
                    jda.getGuilds().size(),
                    jda.getUnavailableGuilds().size());
            ServerSynchronization result = DiscordServerService.getInstance()
                    .synchronize(jda.getGuilds(), jda.getUnavailableGuilds());
            logger.info(
                    "Serveurs synchronisés avec l'API : {} enregistré(s), {} marqué(s) comme quitté(s) {}",
                    result.registered(),
                    result.left().size(),
                    result.left());
        } catch (RuntimeException exception) {
            if (attempt >= MAX_ATTEMPTS) {
                logger.error("Synchronisation des serveurs abandonnée après {} tentatives", attempt, exception);
                return;
            }
            logger.warn(
                    "Synchronisation des serveurs impossible (tentative {}/{}), nouvel essai dans {} s : {}",
                    attempt,
                    MAX_ATTEMPTS,
                    RETRY_DELAY_SECONDS,
                    exception.getMessage());
            retries.schedule(() -> synchronize(jda, attempt + 1), RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }
}
