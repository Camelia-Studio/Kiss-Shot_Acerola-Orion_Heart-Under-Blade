package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.services.AutoBanRoleService;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AutoBanRoleListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoBanRoleListener.class);
    private static final Color COLOR_SUCCESS = new Color(0x6A0DAD);
    private static final Color COLOR_FAILURE = Color.ORANGE;

    private final Set<String> watchedRoleIds;
    private final Set<String> protectedRoleIds;
    private final AutoBanRoleService autoBanRoleService;

    public AutoBanRoleListener() {
        String rawWatchedRoles = Configuration.getInstance().getDotenv().get("AUTO_BAN_ROLE_IDS", "");
        watchedRoleIds = parseIds(rawWatchedRoles);
        autoBanRoleService = new AutoBanRoleService(watchedRoleIds);

        String rawProtectedRoles = Configuration.getInstance().getDotenv().get("AUTO_BAN_EXEMPT_ROLE_IDS", "");
        protectedRoleIds = parseIds(rawProtectedRoles);

        if (!watchedRoleIds.isEmpty()) {
            logger.info("AutoBanRole actif sur {} rôle(s), {} rôle(s) protégé(s)",
                    watchedRoleIds.size(), protectedRoleIds.size());
        }
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        if (watchedRoleIds.isEmpty()) return;

        Member member = event.getMember();
        if (member.getUser().isBot()) return;
        if (member.isOwner()) return;
        if (!event.getGuild().getSelfMember().canInteract(member)) return;
        if (member.getRoles().stream().anyMatch(role -> protectedRoleIds.contains(role.getId()))) return;

        Set<String> addedRoleIds = event.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toUnmodifiableSet());
        Optional<String> matchedRoleId = autoBanRoleService.findWatchedRoleId(addedRoleIds);
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
                            sendLogEmbed(event.getGuild().getTextChannelById(
                                    Configuration.getInstance().getDotenv().get("LOG_CHANNEL_ID", "")),
                                    memberTag, roleLabel, null);
                        },
                        error -> {
                            logger.error("Échec du ban automatique par rôle : {}", error.getMessage());
                            sendLogEmbed(event.getGuild().getTextChannelById(
                                    Configuration.getInstance().getDotenv().get("LOG_CHANNEL_ID", "")),
                                    memberTag, roleLabel, error.getMessage());
                        }
                );
    }

    private void sendLogEmbed(TextChannel logChannel, String memberTag, String roleLabel, String errorReason) {
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

    private static Set<String> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
