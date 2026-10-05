package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.SanctionAction;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ModerationSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigDashboard {
    public static final String MODULE_SELECTOR_ID = "config:dashboard:module";
    private static final String ACTION_PREFIX = "config:dashboard:";

    private ConfigDashboard() {
    }

    public static View create(
            List<ModuleConfiguration> configurations,
            ModuleType selectedModule,
            String notice
    ) {
        return create(configurations, selectedModule, notice, false, false);
    }

    public static View confirmDisable(
            List<ModuleConfiguration> configurations,
            ModuleType selectedModule
    ) {
        return create(
                configurations,
                selectedModule,
                "La désactivation conserve les réglages du module.",
                true,
                false);
    }

    public static View confirmAutoRoleCatchUp(
            List<ModuleConfiguration> configurations,
            ModuleType selectedModule
    ) {
        return create(
                configurations,
                selectedModule,
                "Le rôle sera attribué à tous les membres humains qui ne le possèdent pas encore.",
                false,
                true);
    }

    private static View create(
            List<ModuleConfiguration> configurations,
            ModuleType selectedModule,
            String notice,
            boolean disableConfirmation,
            boolean catchUpConfirmation
    ) {
        ModuleConfiguration selected = configurations.stream()
                .filter(configuration -> configuration.module() == selectedModule)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Configuration de module introuvable"));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Configuration — " + ModuleConfigurationModal.label(selected.module()))
                .setDescription(disableConfirmation
                        ? "Confirmez la désactivation de ce module."
                        : catchUpConfirmation
                        ? "Confirmez le rattrapage manuel du rôle automatique."
                        : "Sélectionnez un module puis utilisez les actions disponibles.")
                .setColor(statusColor(selected.status()))
                .addField("État", statusIcon(selected.status()) + " " + statusLabel(selected.status()), false);

        if (selected.status() == ModuleStatus.SUSPENDED && selected.suspensionReason() != null) {
            embed.addField("Cause de la suspension", selected.suspensionReason(), false);
        }
        addSettings(embed, selected);
        if (notice != null && !notice.isBlank()) {
            embed.setFooter(notice);
        }

        StringSelectMenu selector = StringSelectMenu.create(MODULE_SELECTOR_ID)
                .setPlaceholder("Choisissez un module")
                .setRequiredRange(1, 1)
                .addOptions(configurations.stream()
                        .sorted(Comparator.comparing(configuration -> configuration.module().ordinal()))
                        .map(configuration -> SelectOption
                                .of(ModuleConfigurationModal.label(configuration.module()), configuration.module().name())
                                .withDescription(statusLabel(configuration.status()))
                                .withDefault(configuration.module() == selectedModule))
                        .toList())
                .build();

        List<ActionRow> components = new ArrayList<>();
        components.add(ActionRow.of(selector));
        List<Button> buttons = actionButtons(selected, disableConfirmation, catchUpConfirmation);
        if (!buttons.isEmpty()) {
            components.add(ActionRow.of(buttons));
        }
        return new View(embed.build(), List.copyOf(components));
    }

    public static Optional<Action> actionFrom(String componentId) {
        if (componentId == null || !componentId.startsWith(ACTION_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.substring(ACTION_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            ActionType type = Arrays.stream(ActionType.values())
                    .filter(action -> action.id.equals(parts[0]))
                    .findFirst()
                    .orElse(null);
            if (type == null) {
                return Optional.empty();
            }
            return Optional.of(new Action(type, ModuleType.valueOf(parts[1])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static List<Button> actionButtons(
            ModuleConfiguration configuration,
            boolean disableConfirmation,
            boolean catchUpConfirmation
    ) {
        List<Button> buttons = new ArrayList<>();
        ModuleType module = configuration.module();

        if (disableConfirmation) {
            buttons.add(Button.danger(
                    actionId(ActionType.CONFIRM_DISABLE, module),
                    "Confirmer la désactivation"));
            buttons.add(Button.secondary(actionId(ActionType.CANCEL, module), "Annuler"));
            return buttons;
        }
        if (catchUpConfirmation) {
            buttons.add(Button.success(
                    actionId(ActionType.CONFIRM_CATCH_UP_AUTO_ROLE, module),
                    "Confirmer le rattrapage"));
            buttons.add(Button.secondary(actionId(ActionType.CANCEL, module), "Annuler"));
            return buttons;
        }

        switch (module) {
            case ANTI_RAID -> {
                buttons.add(Button.primary(actionId(ActionType.CONFIGURE_RECENT, module), "Comptes récents"));
                buttons.add(Button.primary(actionId(ActionType.CONFIGURE_MENTIONS, module), "Spam de mentions"));
                buttons.add(Button.secondary(actionId(ActionType.CONFIGURE_RESOURCES, module), "Ressources"));
            }
            case AUTO_SANCTION_CHANNEL, AUTO_SANCTION_ROLE -> {
                buttons.add(Button.primary(actionId(ActionType.CONFIGURE_TRIGGERS, module), "Déclencheurs"));
                buttons.add(Button.primary(actionId(ActionType.CONFIGURE_SANCTION, module), "Action"));
                buttons.add(Button.secondary(actionId(ActionType.CONFIGURE_RESOURCES, module), "Ressources"));
            }
            case MEMBER_AUDIT -> {
                buttons.add(Button.primary(actionId(ActionType.CONFIGURE, module), "Éléments surveillés"));
                buttons.add(Button.secondary(actionId(ActionType.CONFIGURE_RESOURCES, module), "Salon de logs"));
            }
            default -> {
                if (ModuleConfigurationModal.hasEditableSettings(module)) {
                    buttons.add(Button.primary(actionId(ActionType.CONFIGURE, module), "Configurer"));
                }
            }
        }

        if (module == ModuleType.AUTO_ROLE && configuration.status() == ModuleStatus.ACTIVE) {
            buttons.add(Button.secondary(actionId(ActionType.CATCH_UP_AUTO_ROLE, module), "Rattraper"));
        }

        switch (configuration.status()) {
            case DISABLED -> buttons.add(Button.success(actionId(ActionType.ACTIVATE, module), "Activer"));
            case ACTIVE -> buttons.add(Button.danger(actionId(ActionType.DISABLE, module), "Désactiver"));
            case SUSPENDED -> {
                buttons.add(Button.secondary(actionId(ActionType.REVALIDATE, module), "Revalider"));
                buttons.add(Button.danger(actionId(ActionType.DISABLE, module), "Désactiver"));
            }
        }
        return buttons;
    }

    private static String actionId(ActionType action, ModuleType module) {
        return ACTION_PREFIX + action.id + ":" + module.name();
    }

    private static void addSettings(EmbedBuilder embed, ModuleConfiguration configuration) {
        boolean hasSettings = false;
        // L'audit des membres a son propre salon (affiché avec les salons du module) : le salon de logs
        // du serveur ne le concerne pas.
        if (configuration.module() != ModuleType.MEMBER_AUDIT
                && configuration.logChannelId() != null && !configuration.logChannelId().isBlank()) {
            embed.addField("Salon de logs", "<#" + configuration.logChannelId() + ">", false);
            hasSettings = true;
        }

        for (Map.Entry<ModuleResourcePurpose, Set<String>> entry : configuration.roleIds().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                embed.addField(
                        roleLabel(entry.getKey()),
                        mentions(entry.getValue(), "<@&", ">"),
                        false);
                hasSettings = true;
            }
        }
        for (Map.Entry<ModuleResourcePurpose, Set<String>> entry : configuration.channelIds().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                embed.addField(
                        channelLabel(entry.getKey()),
                        mentions(entry.getValue(), "<#", ">"),
                        false);
                hasSettings = true;
            }
        }

        if (configuration.module() == ModuleType.ANTI_RAID) {
            ModerationSettings.AntiRaid settings = ModerationSettings.antiRaid(configuration);
            embed.addField(
                    "Comptes récents",
                    ruleSummary(
                            settings.recentAccountEnabled(),
                            actionLabel(settings.recentAccountAction(), settings.recentAccountTimeout().toSeconds()),
                            "moins de " + settings.recentAccountMaximumAgeDays() + " jour(s)"),
                    false);
            embed.addField(
                    "Spam de mentions",
                    ruleSummary(
                            settings.mentionSpamEnabled(),
                            actionLabel(settings.mentionAction(), settings.mentionTimeout().toSeconds()),
                            settings.mentionLimit() + " mention(s) en " + settings.mentionWindow().toSeconds() + " s")
                            + " · suppression : " + yesNo(settings.deleteMentionMessage()),
                    false);
            hasSettings = true;
        } else if (configuration.module() == ModuleType.MEMBER_AUDIT) {
            ModerationSettings.MemberAudit settings = ModerationSettings.memberAudit(configuration);
            embed.addField("Pseudos surveillés", yesNo(settings.watchNickname()), true);
            embed.addField("Avatars surveillés", yesNo(settings.watchAvatar()), true);
            hasSettings = true;
        } else if (configuration.module() == ModuleType.AUTO_SANCTION_CHANNEL
                || configuration.module() == ModuleType.AUTO_SANCTION_ROLE) {
            ModerationSettings.AutomaticSanction settings = ModerationSettings.automaticSanction(configuration);
            embed.addField(
                    "Action configurée",
                    settings.action()
                            .map(action -> actionLabel(action, settings.timeout().toSeconds()))
                            .orElse("Aucune"),
                    false);
            embed.addField(
                    "Suppression lors d'un bannissement",
                    settings.banHistoryDays() + " jour(s) d'historique",
                    false);
            if (configuration.module() == ModuleType.AUTO_SANCTION_CHANNEL) {
                embed.addField("Suppression du message", yesNo(settings.deleteMessage()), false);
            }
            hasSettings = true;
        }

        if (!hasSettings) {
            embed.addField("Réglages", "Aucun réglage spécifique.", false);
        }
    }

    private static String ruleSummary(boolean enabled, String action, String threshold) {
        return (enabled ? "Activée" : "Désactivée") + " · " + threshold + " · " + action;
    }

    private static String yesNo(boolean value) {
        return value ? "oui" : "non";
    }

    private static String actionLabel(SanctionAction action, long timeoutSeconds) {
        return switch (action) {
            case LOG_ONLY -> "Journalisation uniquement";
            case TIMEOUT -> "Exclusion temporaire (" + timeoutSeconds + " s)";
            case KICK -> "Expulsion";
            case BAN -> "Bannissement";
        };
    }

    private static String mentions(Set<String> ids, String prefix, String suffix) {
        return ids.stream()
                .sorted()
                .map(id -> prefix + id + suffix)
                .collect(Collectors.joining(" "));
    }

    private static String roleLabel(ModuleResourcePurpose purpose) {
        return switch (purpose) {
            case TARGET -> "Rôle cible";
            case WATCHED -> "Rôles surveillés";
            case PROTECTED -> "Rôles protégés";
            case EXCLUDED -> "Rôles exclus";
            case LOG -> "Rôles de logs";
        };
    }

    private static String channelLabel(ModuleResourcePurpose purpose) {
        return switch (purpose) {
            case TARGET -> "Salon cible";
            case WATCHED -> "Salons surveillés";
            case PROTECTED -> "Salons protégés";
            case EXCLUDED -> "Salons exclus";
            case LOG -> "Salon de logs";
        };
    }

    private static int statusColor(ModuleStatus status) {
        return switch (status) {
            case DISABLED -> 0x95A5A6;
            case ACTIVE -> 0x2ECC71;
            case SUSPENDED -> 0xF39C12;
        };
    }

    private static String statusIcon(ModuleStatus status) {
        return switch (status) {
            case DISABLED -> "⚪";
            case ACTIVE -> "🟢";
            case SUSPENDED -> "🟠";
        };
    }

    private static String statusLabel(ModuleStatus status) {
        return switch (status) {
            case DISABLED -> "Désactivé";
            case ACTIVE -> "Actif";
            case SUSPENDED -> "Suspendu";
        };
    }

    public enum ActionType {
        ACTIVATE("activate"),
        CONFIGURE("configure"),
        CONFIGURE_RECENT("configure-recent"),
        CONFIGURE_MENTIONS("configure-mentions"),
        CONFIGURE_TRIGGERS("configure-triggers"),
        CONFIGURE_SANCTION("configure-sanction"),
        CONFIGURE_RESOURCES("configure-resources"),
        CATCH_UP_AUTO_ROLE("catch-up-auto-role"),
        CONFIRM_CATCH_UP_AUTO_ROLE("confirm-catch-up-auto-role"),
        DISABLE("disable"),
        CONFIRM_DISABLE("confirm-disable"),
        CANCEL("cancel"),
        REVALIDATE("revalidate");

        private final String id;

        ActionType(String id) {
            this.id = id;
        }
    }

    public record Action(ActionType type, ModuleType module) {
    }

    public record View(MessageEmbed embed, List<ActionRow> components) {
    }
}
