package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.audio.PlayerManager;
import org.camelia.studio.kiss.shot.acerola.services.DiscordServerService;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.camelia.studio.kiss.shot.acerola.services.recording.RecordingService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildLifecycleListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildLifecycleListener.class);

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        DiscordServerService.getInstance().register(event.getGuild().getId());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        try {
            RecordingService.cleanupGuildIfInitialized(event.getGuild());
        } catch (RuntimeException exception) {
            logger.error("Impossible de nettoyer l'enregistrement du serveur {}", event.getGuild().getId(), exception);
        }
        try {
            PlayerManager.cleanupGuildIfInitialized(event.getGuild());
        } catch (RuntimeException exception) {
            logger.error("Impossible de nettoyer le lecteur du serveur {}", event.getGuild().getId(), exception);
        }
        try {
            DiscordServerService.getInstance().markLeft(event.getGuild().getId());
        } finally {
            ModuleConfigurationService.getInstance().evictGuild(event.getGuild().getId());
        }
    }
}
