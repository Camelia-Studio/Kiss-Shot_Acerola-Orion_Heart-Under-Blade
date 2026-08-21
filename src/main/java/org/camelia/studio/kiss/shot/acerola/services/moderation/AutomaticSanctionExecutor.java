package org.camelia.studio.kiss.shot.acerola.services.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.SanctionAction;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class AutomaticSanctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AutomaticSanctionExecutor.class);
    private static final Color COLOR_SUCCESS = new Color(0x6A0DAD);
    private static final Color COLOR_FAILURE = Color.ORANGE;

    public void execute(
            Member member,
            Message triggerMessage,
            ModuleConfiguration configuration,
            SanctionAction action,
            Duration timeout,
            int banHistoryDays,
            boolean deleteTriggerMessage,
            String rule,
            String details
    ) {
        if (ModerationProtectionService.isProtected(
                member,
                configuration.roles(ModuleResourcePurpose.PROTECTED))) {
            return;
        }

        if (deleteTriggerMessage && triggerMessage != null) {
            deleteTrigger(triggerMessage, configuration, member, rule);
        }

        if (action == SanctionAction.LOG_ONLY) {
            sendLog(configuration, member, action, rule, details, null);
            return;
        }
        if (!member.getGuild().getSelfMember().canInteract(member)) {
            sendLog(
                    configuration,
                    member,
                    action,
                    rule,
                    details,
                    "La hiérarchie des rôles empêche le bot d'agir sur ce membre");
            return;
        }

        AuditableRestAction<Void> restAction = switch (action) {
            case TIMEOUT -> member.timeoutFor(timeout);
            case KICK -> member.kick();
            case BAN -> member.ban(banHistoryDays, TimeUnit.DAYS);
            case LOG_ONLY -> throw new IllegalStateException("Action de journalisation déjà traitée");
        };

        restAction.reason("Sanction automatique : " + rule).queue(
                ignored -> {
                    logger.info("Sanction automatique {} appliquée à {} : {}", action, member.getId(), rule);
                    sendLog(configuration, member, action, rule, details, null);
                },
                error -> {
                    logger.error("Échec de la sanction automatique {} pour {}", action, member.getId(), error);
                    sendLog(configuration, member, action, rule, details, error.getMessage());
                });
    }

    private void deleteTrigger(
            Message message,
            ModuleConfiguration configuration,
            Member member,
            String rule
    ) {
        message.delete().reason("Suppression configurée pour une sanction automatique").queue(
                ignored -> logger.debug("Message déclencheur {} supprimé", message.getId()),
                error -> {
                    logger.error("Impossible de supprimer le message déclencheur {}", message.getId(), error);
                    sendLog(
                            configuration,
                            member,
                            SanctionAction.LOG_ONLY,
                            rule,
                            "Le message déclencheur n'a pas pu être supprimé",
                            error.getMessage());
                });
    }

    private void sendLog(
            ModuleConfiguration configuration,
            Member member,
            SanctionAction action,
            String rule,
            String details,
            String errorReason
    ) {
        GuildMessageChannel logChannel = member.getGuild().getChannelById(
                GuildMessageChannel.class,
                configuration.logChannelId());
        if (logChannel == null) {
            logger.error("Salon de logs indisponible pour la sanction automatique sur {}", member.getGuild().getId());
            return;
        }

        boolean success = errorReason == null;
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(success ? "Modération automatique — Succès" : "Modération automatique — Échec")
                .setColor(success ? COLOR_SUCCESS : COLOR_FAILURE)
                .addField("Utilisateur", member.getUser().getName() + " (" + member.getId() + ")", true)
                .addField("Action", actionLabel(action), true)
                .addField("Règle", rule, false)
                .addField("Détails", details, false);
        if (!success) {
            embed.addField("Raison de l'échec", errorReason, false);
        }
        logChannel.sendMessageEmbeds(embed.build()).queue(
                null,
                error -> logger.error("Impossible d'écrire dans le salon de logs", error));
    }

    private String actionLabel(SanctionAction action) {
        return switch (action) {
            case LOG_ONLY -> "Journalisation uniquement";
            case TIMEOUT -> "Exclusion temporaire";
            case KICK -> "Expulsion";
            case BAN -> "Bannissement";
        };
    }
}
