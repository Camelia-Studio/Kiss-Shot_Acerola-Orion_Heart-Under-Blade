package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discord archive un fil inactif, et JDA le retire alors de son cache. Un fil de logs est rouvert
 * aussitôt pour rester visible (et sélectionnable dans le back-office). Sans la permission de gérer
 * les fils, il sera rouvert par Discord au prochain log publié.
 */
public class LogThreadArchiveListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(LogThreadArchiveListener.class);

    @Override
    public void onChannelUpdateArchived(@NotNull ChannelUpdateArchivedEvent event) {
        if (!event.getChannelType().isThread() || !Boolean.TRUE.equals(event.getNewValue())) return;

        ThreadChannel thread = event.getChannel().asThreadChannel();
        if (thread.isLocked()) return;

        try {
            if (!ModuleConfigurationService.getInstance().logChannelIds(thread.getGuild()).contains(thread.getId())) {
                return;
            }
        } catch (RuntimeException exception) {
            logger.warn("Impossible de vérifier si le fil archivé {} est un salon de logs", thread.getId(), exception);
            return;
        }

        if (!thread.isOwner() && !thread.getGuild().getSelfMember().hasPermission(thread, Permission.MANAGE_THREADS)) {
            logger.info("Fil de logs {} archivé : il sera rouvert au prochain log", thread.getId());
            return;
        }
        thread.getManager().setArchived(false).queue(
                null,
                error -> logger.warn("Impossible de rouvrir le fil de logs {}", thread.getId(), error));
    }
}
