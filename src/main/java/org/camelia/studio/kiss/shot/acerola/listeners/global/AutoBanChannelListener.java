package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AutoBanChannelListener extends ModuleAwareListener {

    private static final Logger logger = LoggerFactory.getLogger(AutoBanChannelListener.class);

    public AutoBanChannelListener() {
        super(ModuleType.AUTO_SANCTION_CHANNEL);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        var configuration = resolved.get();
        Set<String> watchedChannelIds = configuration.channels(ModuleResourcePurpose.WATCHED);
        Set<String> protectedRoleIds = configuration.roles(ModuleResourcePurpose.PROTECTED);
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;

        Member member = event.getMember();
        if (member == null) return;
        if (member.getUser().isBot()) return;
        if (member.isOwner()) return;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return;
        if (!event.getGuild().getSelfMember().canInteract(member)) return;
        if (member.getRoles().stream().anyMatch(role -> protectedRoleIds.contains(role.getId()))) return;

        String channelMention = event.getChannel().getAsMention();
        String memberTag = member.getUser().getName() + " (" + member.getId() + ")";

        event.getGuild().ban(member, 7, TimeUnit.DAYS)
                .reason("Publication dans un salon restreint")
                .queue(
                        success -> {
                            logger.info("Membre banni automatiquement suite à une publication dans un salon surveillé");
                            sendLogEmbed(event.getGuild().getChannelById(
                                    GuildMessageChannel.class,
                                    configuration.logChannelId()),
                                    memberTag, channelMention, null);
                        },
                        error -> {
                            logger.error("Échec du ban automatique : {}", error.getMessage());
                            sendLogEmbed(event.getGuild().getChannelById(
                                    GuildMessageChannel.class,
                                    configuration.logChannelId()),
                                    memberTag, channelMention, error.getMessage());
                        }
                );
    }

    private static final Color COLOR_SUCCESS = new Color(0x6A0DAD);
    private static final Color COLOR_FAILURE = Color.ORANGE;

    private void sendLogEmbed(
            GuildMessageChannel logChannel,
            String memberTag,
            String channelMention,
            String errorReason
    ) {
        if (logChannel == null) return;

        boolean success = errorReason == null;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(success ? "Ban automatique — Succès" : "Ban automatique — Échec")
                .setColor(success ? COLOR_SUCCESS : COLOR_FAILURE)
                .addField("Utilisateur", memberTag, true)
                .addField("Salon", channelMention, true);

        if (!success) {
            embed.addField("Raison de l'échec", errorReason, false);
        }

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }
}
