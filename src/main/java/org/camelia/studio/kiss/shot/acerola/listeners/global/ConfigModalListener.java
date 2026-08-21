package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.commands.configuration.ModuleActivationModal;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ConfigurationOperationResult;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleActivationSettings;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ConfigModalListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ConfigModalListener.class);

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        Optional<ModuleType> selectedModule = ModuleActivationModal.moduleFrom(event.getModalId());
        if (selectedModule.isEmpty()) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Cette configuration ne peut être enregistrée que sur un serveur.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue(hook -> {
            try {
                ModuleType module = selectedModule.get();
                ModuleActivationSettings settings = ModuleActivationModal.settingsFrom(event, module);
                ConfigurationOperationResult result = ModuleConfigurationService.getInstance().activate(
                        event.getGuild(),
                        module,
                        event.getMember().getId(),
                        settings);
                hook.editOriginal(result.message()).queue();
            } catch (RuntimeException exception) {
                logger.error(
                        "Configuration modale indisponible pour le serveur {}",
                        event.getGuild().getId(),
                        exception);
                hook.editOriginal("La configuration est temporairement indisponible. Réessayez plus tard.")
                        .queue();
            }
        });
    }
}
