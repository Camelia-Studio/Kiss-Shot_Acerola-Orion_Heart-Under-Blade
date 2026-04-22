package org.camelia.studio.kiss.shot.acerola.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.camelia.studio.kiss.shot.acerola.models.LeaveSurvey;
import org.camelia.studio.kiss.shot.acerola.repositories.LeaveSurveyRepository;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LeaveSurveyService {
    private static final Logger logger = LoggerFactory.getLogger(LeaveSurveyService.class);
    private final LeaveSurveyRepository repository = new LeaveSurveyRepository();

    // Configurable via LEAVE_SURVEY_BUTTONS_<n>_EMOJI / LEAVE_SURVEY_BUTTONS_<n>_LABEL
    // Par défaut, les 4 choix définis dans la spec
    public static final List<String[]> DEFAULT_BUTTONS = List.of(
        new String[]{"😕", "J'étais perdu·e / Trop de salons / Mal organisé"},
        new String[]{"🔗", "J'ai cliqué sur le lien par inadvertance"},
        new String[]{"😶", "Je ne me suis pas senti·e à l'aise"},
        new String[]{"⭐", "Cette grognasse de Gachamélia n'a pas fait de moi un 5★ !"}
    );

    private long getThresholdHours() {
        String val = Configuration.getInstance().getDotenv().get("LEAVE_SURVEY_THRESHOLD_HOURS", "24");
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 24;
        }
    }

    private long getResponseTtlDays() {
        String val = Configuration.getInstance().getDotenv().get("LEAVE_SURVEY_RESPONSE_TTL_DAYS", "7");
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    private String getDmMessage() {
        return Configuration.getInstance().getDotenv().get(
            "LEAVE_SURVEY_DM_MESSAGE",
            "Nous avons remarqué que tu as quitté Camélia Studio peu après l'avoir rejoint. " +
            "Si tu as un moment, peux-tu nous dire pourquoi ? Cela nous aide beaucoup à améliorer le serveur 💙"
        );
    }

    private String getConfirmMessage() {
        return Configuration.getInstance().getDotenv().get(
            "LEAVE_SURVEY_CONFIRM_MESSAGE",
            "Merci beaucoup ! Nous utiliserons ta réponse afin de faire de Camélia Studio un serveur " +
            "où chacun puisse bien s'y sentir. N'hésite pas à revenir nous voir à l'occasion ❤️ https://discord.gg/nBuZ9vJ"
        );
    }

    public boolean shouldSendSurvey(Instant joinedAt) {
        return Duration.between(joinedAt, Instant.now()).toHours() < getThresholdHours();
    }

    public LeaveSurvey createSurvey(String discordId, String username, String guildId, Instant joinedAt, Instant leftAt) {
        LeaveSurvey survey = new LeaveSurvey(discordId, username, guildId, joinedAt, leftAt);
        return repository.save(survey);
    }

    public void sendSurvey(User user, LeaveSurvey survey) {
        user.openPrivateChannel().queue(
            channel -> channel.sendMessage(getDmMessage())
                .setComponents(buildButtons(survey.getId()))
                .queue(
                    msg -> logger.info("Sondage de départ envoyé à l'utilisateur {}", survey.getId()),
                    err -> logger.info("Échec du DM pour le sondage {} : {}", survey.getId(), err.getMessage())
                ),
            err -> logger.info("Impossible d'ouvrir un canal privé pour le sondage {} : {}", survey.getId(), err.getMessage())
        );
    }

    public void handleButtonResponse(Long surveyId, int buttonIndex, ButtonInteractionEvent event) {
        Optional<LeaveSurvey> optional = repository.findById(surveyId);
        if (optional.isEmpty()) {
            event.deferEdit().queue();
            return;
        }

        LeaveSurvey survey = optional.get();

        if (Duration.between(survey.getLeftAt(), Instant.now()).toDays() >= getResponseTtlDays()) {
            event.editComponents().queue();
            return;
        }

        if (survey.isResponded()) {
            event.deferEdit().queue();
            return;
        }

        String[] btn = DEFAULT_BUTTONS.get(buttonIndex);
        String response = btn[0] + " " + btn[1];
        survey.setResponse(response);
        survey.setResponded(true);
        repository.update(survey);

        event.editMessage(getConfirmMessage()).setComponents().queue();

        logResponse(survey, response, event.getJDA());
    }

    private List<ActionRow> buildButtons(Long surveyId) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < DEFAULT_BUTTONS.size(); i++) {
            String[] btn = DEFAULT_BUTTONS.get(i);
            String label = btn[0] + " " + btn[1];
            if (label.length() > 80) label = label.substring(0, 80);
            buttons.add(Button.secondary("leave_survey:" + surveyId + ":" + i, label));
        }

        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }
        return rows;
    }

    private void logResponse(LeaveSurvey survey, String response, JDA jda) {
        String logChannelId = Configuration.getInstance().getDotenv().get("LEAVE_SURVEY_LOG_CHANNEL_ID", "");
        if (logChannelId.isBlank()) {
            logger.warn("LEAVE_SURVEY_LOG_CHANNEL_ID n'est pas configuré, réponse non loggée");
            return;
        }

        TextChannel channel = jda.getTextChannelById(logChannelId);
        if (channel == null) {
            logger.warn("Salon de log {} introuvable", logChannelId);
            return;
        }

        Duration timeSpent = Duration.between(survey.getJoinedAt(), survey.getLeftAt());
        String timeStr = formatDuration(timeSpent);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")
            .withZone(ZoneId.of("Europe/Paris"));
        String dateStr = formatter.format(Instant.now());

        String message = "📤 Sondage de départ — @" + survey.getUsername() + " (ID: " + survey.getDiscordId() + ")\n" +
            "⏱ Temps passé : " + timeStr + "\n" +
            "✅ Réponse : \"" + response + "\"\n" +
            "📅 Le " + dateStr;

        channel.sendMessage(message).queue(
            null,
            err -> logger.error("Impossible d'envoyer le log dans le salon {} : {}", logChannelId, err.getMessage())
        );
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) return hours + "h" + String.format("%02d", minutes);
        return minutes + "min";
    }
}
