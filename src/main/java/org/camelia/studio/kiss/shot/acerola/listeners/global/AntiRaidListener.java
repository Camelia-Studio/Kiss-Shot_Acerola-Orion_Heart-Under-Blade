package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.AntiRaidService;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.moderation.AutomaticSanctionExecutor;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ModerationSettings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

public class AntiRaidListener extends ModuleAwareListener {

    private static final Logger logger = LoggerFactory.getLogger(AntiRaidListener.class);
    private final AntiRaidService antiRaidService = new AntiRaidService();
    private final AutomaticSanctionExecutor sanctionExecutor = new AutomaticSanctionExecutor();

    public AntiRaidListener() {
        super(ModuleType.ANTI_RAID);
        logger.info("AntiRaid initialisé avec une configuration isolée par serveur");
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        ModuleConfiguration configuration = resolved.get();
        ModerationSettings.AntiRaid settings = ModerationSettings.antiRaid(configuration);
        if (!settings.recentAccountEnabled()) return;
        Member member = event.getMember();

        Instant now = Instant.now();
        Instant createdAt = member.getUser().getTimeCreated().toInstant();
        if (!antiRaidService.isAccountTooYoung(
                createdAt,
                now,
                Duration.ofDays(settings.recentAccountMaximumAgeDays()))) return;

        long accountAgeDays = Duration.between(createdAt, now).toDays();
        String details = "Compte créé il y a " + accountAgeDays
                + " jour(s), seuil minimum " + settings.recentAccountMaximumAgeDays() + " jour(s)";
        sanctionExecutor.execute(
                member,
                null,
                configuration,
                settings.recentAccountAction(),
                settings.recentAccountTimeout(),
                0,
                false,
                "Compte Discord trop récent",
                details);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        ModuleConfiguration configuration = resolved.get();
        ModerationSettings.AntiRaid settings = ModerationSettings.antiRaid(configuration);
        if (!settings.mentionSpamEnabled()) return;

        Member member = event.getMember();
        if (member == null) return;

        int mentionCount = AntiRaidService.countMentionTokens(event.getMessage().getContentRaw());
        if (mentionCount <= 0) return;

        AntiRaidService.MentionSpamResult result = antiRaidService.recordMentions(
                event.getGuild().getId(),
                member.getId(),
                mentionCount,
                Instant.now(),
                settings.mentionLimit(),
                settings.mentionWindow()
        );

        if (!result.thresholdReached()) return;

        String details = result.totalMentions() + " mention(s) utilisateur/rôle dans la fenêtre anti-spam";
        sanctionExecutor.execute(
                member,
                event.getMessage(),
                configuration,
                settings.mentionAction(),
                settings.mentionTimeout(),
                0,
                settings.deleteMentionMessage(),
                "Spam de mentions",
                details);
    }
}
