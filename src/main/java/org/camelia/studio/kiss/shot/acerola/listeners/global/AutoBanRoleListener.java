package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.AutoBanRoleService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AutoBanRoleListener extends ModuleAwareListener {

    private static final Logger logger = LoggerFactory.getLogger(AutoBanRoleListener.class);
    private static final Color COLOR_SUCCESS = new Color(0x6A0DAD);
    private static final Color COLOR_FAILURE = Color.ORANGE;

    public AutoBanRoleListener() {
        super(ModuleType.AUTO_SANCTION_ROLE);
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        var configuration = resolved.get();
        Set<String> watchedRoleIds = configuration.roles(ModuleResourcePurpose.WATCHED);
        Set<String> protectedRoleIds = configuration.roles(ModuleResourcePurpose.PROTECTED);
        if (watchedRoleIds.isEmpty()) return;

        Member member = event.getMember();
        if (member.getUser().isBot()) return;
        if (member.isOwner()) return;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return;
        if (!event.getGuild().getSelfMember().canInteract(member)) return;
        if (member.getRoles().stream().anyMatch(role -> protectedRoleIds.contains(role.getId()))) return;

        Set<String> addedRoleIds = event.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toUnmodifiableSet());
        Optional<String> matchedRoleId = new AutoBanRoleService(watchedRoleIds).findWatchedRoleId(addedRoleIds);
        if (matchedRoleId.isEmpty()) return;

        Role matchedRole = event.getGuild().getRoleById(matchedRoleId.get());
        String roleLabel = matchedRole == null
                ? matchedRoleId.get()
                : matchedRole.getName() + " (" + matchedRole.getId() + ")";
        String memberTag = member.getUser().getName() + " (" + member.getId() + ")";

        event.getGuild().ban(member, 7, TimeUnit.DAYS)
                .reason("Obtention d'un rôle restreint")
                .queue(
                        success -> {
                            logger.info("Membre banni automatiquement suite à l'obtention d'un rôle restreint");
                            sendLogEmbed(event.getGuild().getChannelById(
                                    GuildMessageChannel.class,
                                    configuration.logChannelId()),
                                    memberTag, roleLabel, null);
                        },
                        error -> {
                            logger.error("Échec du ban automatique par rôle : {}", error.getMessage());
                            sendLogEmbed(event.getGuild().getChannelById(
                                    GuildMessageChannel.class,
                                    configuration.logChannelId()),
                                    memberTag, roleLabel, error.getMessage());
                        }
                );
    }

    private void sendLogEmbed(
            GuildMessageChannel logChannel,
            String memberTag,
            String roleLabel,
            String errorReason
    ) {
        if (logChannel == null) return;

        boolean success = errorReason == null;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(success ? "Ban automatique — Succès" : "Ban automatique — Échec")
                .setColor(success ? COLOR_SUCCESS : COLOR_FAILURE)
                .addField("Utilisateur", memberTag, true)
                .addField("Rôle", roleLabel, true);

        if (!success) {
            embed.addField("Raison de l'échec", errorReason, false);
        }

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

}
