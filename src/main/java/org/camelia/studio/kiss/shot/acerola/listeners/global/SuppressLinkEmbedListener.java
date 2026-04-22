package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class SuppressLinkEmbedListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SuppressLinkEmbedListener.class);

    private final Set<String> watchedChannelIds;

    public SuppressLinkEmbedListener() {
        String raw = Configuration.getInstance().getDotenv().get("NO_EMBED_CHANNEL_IDS", "");
        watchedChannelIds = parseIds(raw);

        if (!watchedChannelIds.isEmpty()) {
            logger.info("SuppressLinkEmbed actif sur {} salon(s)", watchedChannelIds.size());
        }
    }

    private static Set<String> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;
        suppressIfNeeded(event.getMessage(), event.getChannel().getId());
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild()) return;
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;
        suppressIfNeeded(event.getMessage(), event.getChannel().getId());
    }

    private void suppressIfNeeded(Message message, String channelId) {
        if (message.getEmbeds().isEmpty()) return;
        message.suppressEmbeds(true).queue(
                success -> logger.info("Intégrations supprimées dans le salon {}", channelId),
                error -> logger.error("Échec de la suppression des intégrations : {}", error.getMessage())
        );
    }
}
