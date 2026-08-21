package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.camelia.studio.kiss.shot.acerola.commands.configuration.ConfigDashboard;
import org.camelia.studio.kiss.shot.acerola.commands.configuration.ModuleConfigurationModal;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ConfigurationOperationResult;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.camelia.studio.kiss.shot.acerola.services.moderation.AutoRoleCatchUpService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ConfigDashboardListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ConfigDashboardListener.class);

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!ConfigDashboard.MODULE_SELECTOR_ID.equals(event.getComponentId())) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null || event.getValues().isEmpty()) {
            return;
        }
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Cette configuration est réservée aux administrateurs.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        ModuleType selected;
        try {
            selected = ModuleType.valueOf(event.getValues().getFirst());
        } catch (IllegalArgumentException exception) {
            return;
        }

        event.deferEdit().queue(hook -> refresh(
                hook,
                event.getGuild(),
                selected,
                null));
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        ConfigDashboard.Action action = ConfigDashboard.actionFrom(event.getComponentId()).orElse(null);
        if (action == null) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Cette configuration ne peut être modifiée que sur un serveur.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Cette configuration est réservée aux administrateurs.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (isConfigurationAction(action.type())) {
            openModal(event, action);
            return;
        }

        if (action.type() == ConfigDashboard.ActionType.DISABLE) {
            event.deferEdit().queue(hook -> showDisableConfirmation(
                    hook,
                    event.getGuild(),
                    action.module()));
            return;
        }
        if (action.type() == ConfigDashboard.ActionType.CANCEL) {
            event.deferEdit().queue(hook -> refresh(
                    hook,
                    event.getGuild(),
                    action.module(),
                    null));
            return;
        }
        if (action.type() == ConfigDashboard.ActionType.CATCH_UP_AUTO_ROLE) {
            event.deferEdit().queue(hook -> showCatchUpConfirmation(hook, event.getGuild()));
            return;
        }
        if (action.type() == ConfigDashboard.ActionType.CONFIRM_CATCH_UP_AUTO_ROLE) {
            event.deferEdit().queue(hook -> runAutoRoleCatchUp(
                    hook,
                    event.getGuild(),
                    event.getMember().getId()));
            return;
        }

        event.deferEdit().queue(hook -> {
            ModuleConfigurationService service = ModuleConfigurationService.getInstance();
            try {
                ConfigurationOperationResult result = switch (action.type()) {
                    case ACTIVATE -> service.activate(
                            event.getGuild(),
                            action.module(),
                            event.getMember().getId());
                    case CONFIRM_DISABLE -> service.disable(
                            event.getGuild(),
                            action.module(),
                            event.getMember().getId());
                    case REVALIDATE -> service.revalidate(
                            event.getGuild(),
                            action.module(),
                            event.getMember().getId());
                    case CONFIGURE,
                         CONFIGURE_RECENT,
                         CONFIGURE_MENTIONS,
                         CONFIGURE_TRIGGERS,
                         CONFIGURE_SANCTION,
                         CONFIGURE_RESOURCES -> throw new IllegalStateException("La configuration nécessite une modale");
                    case DISABLE,
                         CANCEL,
                         CATCH_UP_AUTO_ROLE,
                         CONFIRM_CATCH_UP_AUTO_ROLE -> throw new IllegalStateException("Action intermédiaire inattendue");
                };
                refresh(hook, event.getGuild(), action.module(), result.message());
            } catch (RuntimeException exception) {
                logger.error(
                        "Action {} indisponible pour le serveur {}",
                        action.type(),
                        event.getGuild().getId(),
                        exception);
                refresh(
                        hook,
                        event.getGuild(),
                        action.module(),
                        "La configuration est temporairement indisponible.");
            }
        });
    }

    private void openModal(ButtonInteractionEvent event, ConfigDashboard.Action action) {
        try {
            ModuleConfiguration configuration = ModuleConfigurationService.getInstance()
                    .find(event.getGuild(), action.module())
                    .orElse(null);
            if (configuration == null) {
                event.reply("Configuration de module introuvable.").setEphemeral(true).queue();
                return;
            }
            event.replyModal(ModuleConfigurationModal.create(
                    event.getGuild(),
                    configuration,
                    ModuleConfigurationModal.SubmissionAction.CONFIGURE,
                    sectionFor(action.type()))).queue();
        } catch (RuntimeException exception) {
            logger.error(
                    "Impossible d'ouvrir la configuration du module {} pour le serveur {}",
                    action.module(),
                    event.getGuild().getId(),
                    exception);
            event.reply("La configuration est temporairement indisponible.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private boolean isConfigurationAction(ConfigDashboard.ActionType action) {
        return switch (action) {
            case CONFIGURE,
                 CONFIGURE_RECENT,
                 CONFIGURE_MENTIONS,
                 CONFIGURE_TRIGGERS,
                 CONFIGURE_SANCTION,
                 CONFIGURE_RESOURCES -> true;
            default -> false;
        };
    }

    private ModuleConfigurationModal.Section sectionFor(ConfigDashboard.ActionType action) {
        return switch (action) {
            case CONFIGURE -> ModuleConfigurationModal.Section.DEFAULT;
            case CONFIGURE_RECENT -> ModuleConfigurationModal.Section.RECENT_ACCOUNT;
            case CONFIGURE_MENTIONS -> ModuleConfigurationModal.Section.MENTION_SPAM;
            case CONFIGURE_TRIGGERS -> ModuleConfigurationModal.Section.TRIGGERS;
            case CONFIGURE_SANCTION -> ModuleConfigurationModal.Section.SANCTION;
            case CONFIGURE_RESOURCES -> ModuleConfigurationModal.Section.RESOURCES;
            default -> throw new IllegalArgumentException("Cette action n'ouvre aucune configuration");
        };
    }

    private void showCatchUpConfirmation(InteractionHook hook, Guild guild) {
        try {
            List<ModuleConfiguration> configurations = ModuleConfigurationService.getInstance().list(guild);
            ConfigDashboard.View dashboard = ConfigDashboard.confirmAutoRoleCatchUp(
                    configurations,
                    ModuleType.AUTO_ROLE);
            hook.editOriginalEmbeds(dashboard.embed())
                    .setComponents(dashboard.components())
                    .queue();
        } catch (RuntimeException exception) {
            logger.error("Impossible d'afficher la confirmation de rattrapage du serveur {}", guild.getId(), exception);
            hook.editOriginal("La configuration est temporairement indisponible.").queue();
        }
    }

    private void runAutoRoleCatchUp(InteractionHook hook, Guild guild, String actorId) {
        ModuleConfigurationService service = ModuleConfigurationService.getInstance();
        ModuleConfiguration configuration = service.activeConfiguration(guild, ModuleType.AUTO_ROLE).orElse(null);
        if (configuration == null) {
            refresh(hook, guild, ModuleType.AUTO_ROLE, "Le rôle automatique doit être actif et valide.");
            return;
        }
        String roleId = configuration.roles(ModuleResourcePurpose.TARGET).stream().findFirst().orElse(null);
        var role = roleId == null ? null : guild.getRoleById(roleId);
        if (role == null) {
            refresh(hook, guild, ModuleType.AUTO_ROLE, "Le rôle automatique configuré n'existe plus.");
            return;
        }

        hook.editOriginal("Rattrapage en cours…").setComponents().queue();
        new AutoRoleCatchUpService().catchUp(guild, role, result -> {
            logger.info(
                    "Rattrapage auto-rôle demandé par {} sur {} : {} attribution(s), {} échec(s)",
                    actorId,
                    guild.getId(),
                    result.assigned(),
                    result.failed());
            refresh(hook, guild, ModuleType.AUTO_ROLE, result.message());
        });
    }

    private void refresh(
            InteractionHook hook,
            Guild guild,
            ModuleType selected,
            String notice
    ) {
        try {
            List<ModuleConfiguration> configurations = ModuleConfigurationService.getInstance()
                    .list(guild);
            ConfigDashboard.View dashboard = ConfigDashboard.create(configurations, selected, notice);
            hook.editOriginalEmbeds(dashboard.embed())
                    .setComponents(dashboard.components())
                    .queue();
        } catch (RuntimeException exception) {
            logger.error("Impossible d'actualiser le tableau de bord du serveur {}", guild.getId(), exception);
            hook.editOriginal("La configuration est temporairement indisponible.").queue();
        }
    }

    private void showDisableConfirmation(
            InteractionHook hook,
            Guild guild,
            ModuleType selected
    ) {
        try {
            List<ModuleConfiguration> configurations = ModuleConfigurationService.getInstance().list(guild);
            ConfigDashboard.View dashboard = ConfigDashboard.confirmDisable(configurations, selected);
            hook.editOriginalEmbeds(dashboard.embed())
                    .setComponents(dashboard.components())
                    .queue();
        } catch (RuntimeException exception) {
            logger.error(
                    "Impossible d'afficher la confirmation de désactivation du serveur {}",
                    guild.getId(),
                    exception);
            hook.editOriginal("La configuration est temporairement indisponible.").queue();
        }
    }
}
