package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AutoBanChannelListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoBanChannelListener.class);

    private final Set<String> watchedChannelIds;

    public AutoBanChannelListener() {
        String raw = Configuration.getInstance().getDotenv().get("AUTO_BAN_CHANNEL_IDS", "");
        if (raw == null || raw.isBlank()) {
            watchedChannelIds = Set.of();
        } else {
            watchedChannelIds = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (!watchedChannelIds.isEmpty()) {
            logger.info("AutoBanChannel actif sur {} salon(s)", watchedChannelIds.size());
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;

        Member member = event.getMember();
        if (member == null) return;
        if (member.getUser().isBot()) return;

        event.getMessage().delete().queue(null, err -> {});

        event.getGuild().ban(member, 0, TimeUnit.SECONDS)
                .reason("Publication dans un salon restreint")
                .queue(
                        success -> logger.info("Membre banni automatiquement suite à une publication dans un salon surveillé"),
                        error -> logger.error("Échec du ban automatique : {}", error.getMessage())
                );
    }
}
