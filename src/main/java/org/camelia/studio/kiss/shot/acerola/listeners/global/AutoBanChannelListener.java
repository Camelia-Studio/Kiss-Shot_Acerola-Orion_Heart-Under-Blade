package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AutoBanChannelListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoBanChannelListener.class);

    private final Set<String> watchedChannelIds;
    private final Set<String> protectedRoleIds;

    public AutoBanChannelListener() {
        String rawChannels = Configuration.getInstance().getDotenv().get("AUTO_BAN_CHANNEL_IDS", "");
        watchedChannelIds = parseIds(rawChannels);

        String rawRoles = Configuration.getInstance().getDotenv().get("AUTO_BAN_EXEMPT_ROLE_IDS", "");
        protectedRoleIds = parseIds(rawRoles);

        if (!watchedChannelIds.isEmpty()) {
            logger.info("AutoBanChannel actif sur {} salon(s), {} rôle(s) protégé(s)",
                    watchedChannelIds.size(), protectedRoleIds.size());
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

        Member member = event.getMember();
        if (member == null) return;
        if (member.getUser().isBot()) return;
        if (member.isOwner()) return;
        if (!event.getGuild().getSelfMember().canInteract(member)) return;
        if (member.getRoles().stream().anyMatch(role -> protectedRoleIds.contains(role.getId()))) return;

        String channelMention = event.getChannel().getAsMention();
        String memberMention = member.getAsMention();

        event.getGuild().ban(member, 7, TimeUnit.DAYS)
                .reason("Publication dans un salon restreint")
                .queue(
                        success -> {
                            logger.info("Membre banni automatiquement suite à une publication dans un salon surveillé");
                            sendLogEmbed(event.getGuild().getTextChannelById(
                                    Configuration.getInstance().getDotenv().get("LOG_CHANNEL_ID", "")),
                                    memberMention, channelMention, null);
                        },
                        error -> {
                            logger.error("Échec du ban automatique : {}", error.getMessage());
                            sendLogEmbed(event.getGuild().getTextChannelById(
                                    Configuration.getInstance().getDotenv().get("LOG_CHANNEL_ID", "")),
                                    memberMention, channelMention, error.getMessage());
                        }
                );
    }

    private void sendLogEmbed(TextChannel logChannel, String memberMention, String channelMention, String errorReason) {
        if (logChannel == null) return;

        boolean success = errorReason == null;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(success ? "Ban automatique — Succès" : "Ban automatique — Échec")
                .setColor(success ? Color.RED : Color.ORANGE)
                .addField("Utilisateur", memberMention, true)
                .addField("Salon", channelMention, true);

        if (!success) {
            embed.addField("Raison de l'échec", errorReason, false);
        }

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }
}
