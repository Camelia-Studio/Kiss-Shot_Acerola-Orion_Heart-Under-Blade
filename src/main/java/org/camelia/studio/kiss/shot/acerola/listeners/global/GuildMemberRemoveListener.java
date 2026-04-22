package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.models.LeaveSurvey;
import org.camelia.studio.kiss.shot.acerola.services.LeaveSurveyService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

public class GuildMemberRemoveListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildMemberRemoveListener.class);
    private final LeaveSurveyService service = new LeaveSurveyService();

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        User user = event.getUser();
        if (user.isBot()) return;

        Member member = event.getMember();
        if (member == null) {
            logger.info("Membre non mis en cache à la sortie, sondage ignoré");
            return;
        }

        Instant joinedAt = member.getTimeJoined().toInstant();
        Instant leftAt = Instant.now();

        if (!service.shouldSendSurvey(joinedAt)) return;

        String guildId = event.getGuild().getId();

        event.getGuild().retrieveAuditLogs().limit(20).queue(
            logs -> {
                boolean wasBannedOrKicked = logs.stream().anyMatch(log ->
                    (log.getType() == ActionType.BAN || log.getType() == ActionType.KICK) &&
                    log.getTargetId().equals(user.getId()) &&
                    Duration.between(log.getTimeCreated().toInstant(), Instant.now()).abs().toSeconds() < 30
                );

                if (wasBannedOrKicked) {
                    logger.info("Sondage ignoré : membre banni ou expulsé");
                    return;
                }

                LeaveSurvey survey = service.createSurvey(user.getId(), user.getName(), guildId, joinedAt, leftAt);
                service.sendSurvey(user, survey);
            },
            err -> {
                logger.warn("Audit log inaccessible, sondage envoyé sans vérification : {}", err.getMessage());
                LeaveSurvey survey = service.createSurvey(user.getId(), user.getName(), guildId, joinedAt, leftAt);
                service.sendSurvey(user, survey);
            }
        );
    }
}
