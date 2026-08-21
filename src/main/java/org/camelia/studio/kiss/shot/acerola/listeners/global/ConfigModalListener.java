package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.camelia.studio.kiss.shot.acerola.commands.configuration.ConfigDashboard;
import org.camelia.studio.kiss.shot.acerola.commands.configuration.ModuleConfigurationModal;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ConfigurationOperationResult;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationSettings;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ConfigModalListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ConfigModalListener.class);

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        ModuleConfigurationModal.Submission submission = ModuleConfigurationModal
                .submissionFrom(event.getModalId())
                .orElse(null);
        if (submission == null) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Cette configuration ne peut être enregistrée que sur un serveur.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getMessage() == null) {
            event.deferReply(true).queue(hook -> process(event, submission, hook, false));
        } else {
            event.deferEdit().queue(hook -> process(event, submission, hook, true));
        }
    }

    private void process(
            ModalInteractionEvent event,
            ModuleConfigurationModal.Submission submission,
            InteractionHook hook,
            boolean refreshDashboard
    ) {
        try {
            ModuleConfigurationSettings settings = ModuleConfigurationModal.settingsFrom(
                    event,
                    submission.module());
            ModuleConfigurationService service = ModuleConfigurationService.getInstance();
            ConfigurationOperationResult result = switch (submission.action()) {
                case ACTIVATE -> service.activate(
                        event.getGuild(),
                        submission.module(),
                        event.getMember().getId(),
                        settings);
                case CONFIGURE -> service.configure(
                        event.getGuild(),
                        submission.module(),
                        event.getMember().getId(),
                        settings);
            };
            if (!refreshDashboard) {
                hook.editOriginal(result.message()).queue();
                return;
            }

            List<ModuleConfiguration> configurations = service.list(event.getGuild());
            ConfigDashboard.View dashboard = ConfigDashboard.create(
                    configurations,
                    submission.module(),
                    result.message());
            hook.editOriginalEmbeds(dashboard.embed())
                    .setComponents(dashboard.components())
                    .queue();
        } catch (RuntimeException exception) {
            logger.error(
                    "Configuration modale indisponible pour le serveur {}",
                    event.getGuild().getId(),
                    exception);
            hook.editOriginal("La configuration est temporairement indisponible. Réessayez plus tard.")
                    .queue();
        }
    }
}
