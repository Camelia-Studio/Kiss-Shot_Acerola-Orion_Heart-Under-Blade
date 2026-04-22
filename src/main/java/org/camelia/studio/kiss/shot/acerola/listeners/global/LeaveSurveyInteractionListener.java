package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.services.LeaveSurveyService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaveSurveyInteractionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(LeaveSurveyInteractionListener.class);
    private final LeaveSurveyService service = new LeaveSurveyService();

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("leave_survey:")) return;

        String[] parts = componentId.split(":");
        if (parts.length != 3) {
            logger.warn("ID de composant leave_survey malformé : {}", componentId);
            event.deferEdit().queue();
            return;
        }

        try {
            long surveyId = Long.parseLong(parts[1]);
            int buttonIndex = Integer.parseInt(parts[2]);

            if (buttonIndex < 0 || buttonIndex >= LeaveSurveyService.DEFAULT_BUTTONS.size()) {
                logger.warn("Index de bouton hors limites : {}", buttonIndex);
                event.deferEdit().queue();
                return;
            }

            service.handleButtonResponse(surveyId, buttonIndex, event);
        } catch (NumberFormatException e) {
            logger.warn("Valeur non numérique dans l'ID de composant : {}", componentId);
            event.deferEdit().queue();
        }
    }
}
