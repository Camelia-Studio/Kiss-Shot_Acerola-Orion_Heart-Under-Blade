package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.AntiRaidService;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AntiRaidListener extends ModuleAwareListener {

    private static final Logger logger = LoggerFactory.getLogger(AntiRaidListener.class);
    private static final Color COLOR_SUCCESS = new Color(0x6A0DAD);
    private static final Color COLOR_FAILURE = Color.ORANGE;

    private final AntiRaidService antiRaidService;
    private final int minimumAccountAgeDays;

    public AntiRaidListener() {
        super(ModuleType.ANTI_RAID);
        int mentionLimit = getPositiveInt("ANTI_RAID_MENTION_LIMIT", 5);
        int mentionWindowSeconds = getPositiveInt("ANTI_RAID_MENTION_WINDOW_SECONDS", 10);
        minimumAccountAgeDays = getPositiveInt("ANTI_RAID_MIN_ACCOUNT_AGE_DAYS", 7);

        antiRaidService = new AntiRaidService(
                mentionLimit,
                Duration.ofSeconds(mentionWindowSeconds),
                Duration.ofDays(minimumAccountAgeDays)
        );

        logger.info(
                "AntiRaid initialisé : {} mention(s) en {}s, compte minimum {} jour(s)",
                mentionLimit,
                mentionWindowSeconds,
                minimumAccountAgeDays
        );
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        ModuleConfiguration configuration = resolved.get();
        Member member = event.getMember();
        if (shouldIgnore(member, configuration)) return;

        Instant now = Instant.now();
        Instant createdAt = member.getUser().getTimeCreated().toInstant();
        if (!antiRaidService.isAccountTooYoung(createdAt, now)) return;

        long accountAgeDays = Duration.between(createdAt, now).toDays();
        String details = "Compte créé il y a " + accountAgeDays
                + " jour(s), seuil minimum " + minimumAccountAgeDays + " jour(s)";
        banAndLog(member, configuration, "Compte Discord trop récent", details);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        ModuleConfiguration configuration = resolved.get();

        Member member = event.getMember();
        if (member == null) return;
        if (shouldIgnore(member, configuration)) return;

        int mentionCount = AntiRaidService.countMentionTokens(event.getMessage().getContentRaw());
        if (mentionCount <= 0) return;

        AntiRaidService.MentionSpamResult result = antiRaidService.recordMentions(
                event.getGuild().getId(),
                member.getId(),
                mentionCount,
                Instant.now()
        );

        if (!result.thresholdReached()) return;

        String details = result.totalMentions() + " mention(s) utilisateur/rôle dans la fenêtre anti-spam";
        banAndLog(member, configuration, "Spam de mentions", details);
    }

    private boolean shouldIgnore(Member member, ModuleConfiguration configuration) {
        if (member.getUser().isBot()) return true;
        if (member.isOwner()) return true;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        if (!member.getGuild().getSelfMember().canInteract(member)) return true;
        Set<String> protectedRoleIds = configuration.roles(ModuleResourcePurpose.PROTECTED);
        return member.getRoles().stream().anyMatch(role -> protectedRoleIds.contains(role.getId()));
    }

    private void banAndLog(
            Member member,
            ModuleConfiguration configuration,
            String rule,
            String details
    ) {
        String memberTag = member.getUser().getName() + " (" + member.getId() + ")";

        member.getGuild().ban(member, 7, TimeUnit.DAYS)
                .reason("Anti-raid : " + rule)
                .queue(
                        success -> {
                            logger.info("Membre banni automatiquement par AntiRaid : {} - {}", rule, details);
                            sendLogEmbed(member.getGuild().getTextChannelById(
                                    configuration.logChannelId()),
                                    memberTag, rule, details, null);
                        },
                        error -> {
                            logger.error("Échec du ban AntiRaid : {}", error.getMessage());
                            sendLogEmbed(member.getGuild().getTextChannelById(
                                    configuration.logChannelId()),
                                    memberTag, rule, details, error.getMessage());
                        }
                );
    }

    private void sendLogEmbed(
            TextChannel logChannel,
            String memberTag,
            String rule,
            String details,
            String errorReason
    ) {
        if (logChannel == null) return;

        boolean success = errorReason == null;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(success ? "Anti-raid — Succès" : "Anti-raid — Échec")
                .setColor(success ? COLOR_SUCCESS : COLOR_FAILURE)
                .addField("Utilisateur", memberTag, true)
                .addField("Règle", rule, true)
                .addField("Détails", details, false);

        if (!success) {
            embed.addField("Raison de l'échec", errorReason, false);
        }

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    private static int getPositiveInt(String key, int defaultValue) {
        String rawValue = Configuration.getInstance().getDotenv().get(key, String.valueOf(defaultValue));
        try {
            int value = Integer.parseInt(rawValue);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Fallback to the default below.
        }
        logger.warn("Configuration {} invalide ({}), utilisation de {}", key, rawValue, defaultValue);
        return defaultValue;
    }
}
