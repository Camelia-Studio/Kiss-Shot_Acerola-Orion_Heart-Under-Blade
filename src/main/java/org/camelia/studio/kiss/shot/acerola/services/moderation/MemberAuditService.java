package org.camelia.studio.kiss.shot.acerola.services.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;

/**
 * Journalise dans le salon de logs les changements de pseudo et d'avatar d'un membre, pour repérer
 * les usurpations d'identité (pseudo ou avatar copiés sur un membre important du serveur).
 */
public class MemberAuditService {
    private static final Logger logger = LoggerFactory.getLogger(MemberAuditService.class);
    private static final Color COLOR_NICKNAME = new Color(0x3498DB);
    private static final Color COLOR_AVATAR = new Color(0xE67E22);
    private static final int MAX_FIELD_LENGTH = 1024;
    private static final String EMPTY_VALUE = "*Aucun*";

    public enum Kind {
        GUILD_NICKNAME("Pseudo du serveur", false),
        GLOBAL_NAME("Pseudo global", false),
        USERNAME("Nom d'utilisateur", false),
        GUILD_AVATAR("Avatar du serveur", true),
        GLOBAL_AVATAR("Avatar global", true);

        private final String label;
        private final boolean avatar;

        Kind(String label, boolean avatar) {
            this.label = label;
            this.avatar = avatar;
        }

        public String label() {
            return label;
        }

        public boolean isAvatar() {
            return avatar;
        }
    }

    public void recordNameChange(
            Guild guild,
            ModuleConfiguration configuration,
            User user,
            Kind kind,
            String oldValue,
            String newValue
    ) {
        if (kind.isAvatar()) {
            throw new IllegalArgumentException("Un changement d'avatar doit passer par recordAvatarChange");
        }
        send(guild, configuration, embed(guild.getMember(user), user, kind, oldValue, newValue));
    }

    public void recordAvatarChange(
            Guild guild,
            ModuleConfiguration configuration,
            User user,
            Kind kind,
            String oldAvatarUrl,
            String newAvatarUrl
    ) {
        if (!kind.isAvatar()) {
            throw new IllegalArgumentException("Un changement de pseudo doit passer par recordNameChange");
        }
        send(guild, configuration, embed(guild.getMember(user), user, kind, oldAvatarUrl, newAvatarUrl));
    }

    /**
     * Pour un changement de pseudo, {@code oldValue} et {@code newValue} sont les pseudos ; pour un changement
     * d'avatar, ce sont les URL des images (ou {@code null} si l'avatar par défaut est utilisé).
     */
    static MessageEmbed embed(Member member, User user, Kind kind, String oldValue, String newValue) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Audit des membres — " + kind.label())
                .setColor(kind.isAvatar() ? COLOR_AVATAR : COLOR_NICKNAME)
                .setTimestamp(Instant.now())
                .addField("Utilisateur", user.getAsMention() + " (" + sanitize(user.getName()) + ", " + user.getId() + ")", false);

        if (kind.isAvatar()) {
            embed.addField("Avatar précédent", imageLabel(oldValue), true)
                    .addField("Nouvel avatar", imageLabel(newValue), true);
            if (oldValue != null) embed.setThumbnail(oldValue);
            if (newValue != null) embed.setImage(newValue);
        } else {
            embed.addField("Ancienne valeur", textLabel(oldValue), true)
                    .addField("Nouvelle valeur", textLabel(newValue), true);
        }

        embed.addField("Compte créé", TimeFormat.RELATIVE.format(user.getTimeCreated()), true);
        if (member != null) {
            embed.addField("Arrivé sur le serveur", TimeFormat.RELATIVE.format(member.getTimeJoined()), true);
        }
        return embed.build();
    }

    private void send(Guild guild, ModuleConfiguration configuration, MessageEmbed embed) {
        String logChannelId = configuration.channels(ModuleResourcePurpose.LOG).stream().findFirst().orElse(null);
        GuildMessageChannel logChannel = logChannelId == null
                ? null
                : guild.getChannelById(GuildMessageChannel.class, logChannelId);
        if (logChannel == null) {
            logger.error("Salon de logs indisponible pour l'audit des membres sur {}", guild.getId());
            return;
        }
        logChannel.sendMessageEmbeds(embed).queue(
                null,
                error -> logger.error("Impossible d'écrire l'audit des membres dans le salon de logs", error));
    }

    private static String textLabel(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY_VALUE;
        }
        return truncate(sanitize(value));
    }

    private static String imageLabel(String url) {
        return url == null ? "*Avatar par défaut*" : "[Voir l'image](" + url + ")";
    }

    /**
     * Un pseudo ne doit pas pouvoir injecter de mise en forme dans le log, ni se faire passer pour un lien
     * cliquable ({@code [texte](url)}), que {@link MarkdownSanitizer} n'échappe pas.
     */
    private static String sanitize(String value) {
        return MarkdownSanitizer.escape(value)
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private static String truncate(String value) {
        return value.length() <= MAX_FIELD_LENGTH ? value : value.substring(0, MAX_FIELD_LENGTH - 1) + "…";
    }
}
