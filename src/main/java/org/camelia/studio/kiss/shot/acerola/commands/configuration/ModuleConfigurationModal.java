package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.SanctionAction;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationSettings;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ModerationSettings;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ModuleConfigurationModal {
    private static final String MODAL_PREFIX = "config:";
    private static final String TARGET_ROLE = "target_role";
    private static final String WATCHED_ROLES = "watched_roles";
    private static final String PROTECTED_ROLES = "protected_roles";
    private static final String WATCHED_CHANNELS = "watched_channels";
    private static final String EXCLUDED_CHANNELS = "excluded_channels";
    private static final String LOG_CHANNEL = "log_channel";
    private static final String RULE_MODE = "rule_mode";
    private static final String ACCOUNT_AGE_DAYS = "account_age_days";
    private static final String MENTION_LIMIT = "mention_limit";
    private static final String MENTION_WINDOW_SECONDS = "mention_window_seconds";
    private static final String TIMEOUT_SECONDS = "timeout_seconds";
    private static final String DELETE_MESSAGE = "delete_message";
    private static final String SANCTION_ACTION = "sanction_action";
    private static final String BAN_HISTORY_DAYS = "ban_history_days";
    private static final int MAX_SELECTIONS = 25;

    private ModuleConfigurationModal() {
    }

    public static boolean requiresConfigurationForActivation(ModuleType module) {
        return switch (module) {
            case AUTO_ROLE,
                 INTEGRATION_REMOVAL,
                 ANTI_RAID,
                 AUTO_SANCTION_CHANNEL,
                 AUTO_SANCTION_ROLE -> true;
            default -> false;
        };
    }

    public static boolean hasEditableSettings(ModuleType module) {
        return module == ModuleType.LINK_ENRICHMENT || requiresConfigurationForActivation(module);
    }

    public static Modal create(
            Guild guild,
            ModuleConfiguration configuration,
            SubmissionAction action
    ) {
        return create(guild, configuration, action, defaultSection(configuration.module()));
    }

    public static Modal create(
            Guild guild,
            ModuleConfiguration configuration,
            SubmissionAction action,
            Section section
    ) {
        ModuleType module = configuration.module();
        if (!supportsSection(module, section)) {
            throw new IllegalArgumentException("Section de configuration incompatible avec ce module");
        }
        Modal.Builder modal = Modal.create(
                MODAL_PREFIX + action.id() + ":" + module.name() + ":" + section.name(),
                modalTitle(module, section));

        switch (section) {
            case DEFAULT -> {
                if (module == ModuleType.AUTO_ROLE) modal.addComponents(roleSelector(
                    guild,
                    TARGET_ROLE,
                    "Rôle à attribuer",
                    "Choisissez le rôle attribué aux nouveaux membres.",
                    configuration.roles(ModuleResourcePurpose.TARGET),
                    true,
                    1));
                else if (module == ModuleType.INTEGRATION_REMOVAL) modal.addComponents(channelSelector(
                    guild,
                    WATCHED_CHANNELS,
                    "Salons surveillés",
                    "Choisissez les salons où les intégrations seront supprimées.",
                    configuration.channels(ModuleResourcePurpose.WATCHED),
                    true,
                    MAX_SELECTIONS));
                else if (module == ModuleType.LINK_ENRICHMENT) modal.addComponents(channelSelector(
                    guild,
                    EXCLUDED_CHANNELS,
                    "Salons exclus (facultatif)",
                    "Les liens publiés dans ces salons ne seront pas enrichis.",
                    configuration.channels(ModuleResourcePurpose.EXCLUDED),
                    false,
                    MAX_SELECTIONS));
                else throw new IllegalArgumentException("Section de configuration incompatible avec ce module");
            }
            case RESOURCES -> modal.addComponents(
                    logChannelSelector(guild, configuration.logChannelId()),
                    protectedRolesSelector(guild, configuration));
            case TRIGGERS -> {
                if (module == ModuleType.AUTO_SANCTION_CHANNEL) modal.addComponents(channelSelector(
                            guild,
                            WATCHED_CHANNELS,
                            "Salons déclencheurs",
                            "Un message dans l'un de ces salons déclenche la sanction.",
                            configuration.channels(ModuleResourcePurpose.WATCHED),
                            true,
                            MAX_SELECTIONS));
                else if (module == ModuleType.AUTO_SANCTION_ROLE) modal.addComponents(roleSelector(
                            guild,
                            WATCHED_ROLES,
                            "Rôles déclencheurs",
                            "L'obtention de l'un de ces rôles déclenche la sanction.",
                            configuration.roles(ModuleResourcePurpose.WATCHED),
                            true,
                            MAX_SELECTIONS));
                else throw new IllegalArgumentException("Section de déclencheurs incompatible avec ce module");
            }
            case RECENT_ACCOUNT -> addRecentAccountSettings(modal, configuration);
            case MENTION_SPAM -> addMentionSpamSettings(modal, configuration);
            case SANCTION -> addSanctionSettings(modal, configuration);
        }

        return modal.build();
    }

    public static Optional<Submission> submissionFrom(String modalId) {
        if (modalId == null || !modalId.startsWith(MODAL_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = modalId.substring(MODAL_PREFIX.length()).split(":", 3);
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            SubmissionAction action = SubmissionAction.fromId(parts[0]);
            ModuleType module = ModuleType.valueOf(parts[1]);
            Section section = parts.length == 3 ? Section.valueOf(parts[2]) : defaultSection(module);
            return hasEditableSettings(module) && supportsSection(module, section)
                    ? Optional.of(new Submission(action, module, section))
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static ModuleConfigurationSettings settingsFrom(ModalInteractionEvent event, ModuleType module) {
        return settingsFrom(event, module, defaultSection(module));
    }

    public static ModuleConfigurationSettings settingsFrom(
            ModalInteractionEvent event,
            ModuleType module,
            Section section
    ) {
        if (!supportsSection(module, section)) {
            throw new IllegalArgumentException("Section de configuration incompatible avec ce module");
        }
        Map<ModuleResourcePurpose, Set<String>> roles = new EnumMap<>(ModuleResourcePurpose.class);
        Map<ModuleResourcePurpose, Set<String>> channels = new EnumMap<>(ModuleResourcePurpose.class);
        String logChannelId = null;

        Map<ModuleSetting, String> settings = Map.of();

        switch (section) {
            case DEFAULT -> {
                if (module == ModuleType.AUTO_ROLE) roles.put(
                        ModuleResourcePurpose.TARGET,
                        selectedRoleIds(event, TARGET_ROLE));
                else if (module == ModuleType.INTEGRATION_REMOVAL) channels.put(
                    ModuleResourcePurpose.WATCHED,
                    selectedChannelIds(event, WATCHED_CHANNELS));
                else if (module == ModuleType.LINK_ENRICHMENT) channels.put(
                    ModuleResourcePurpose.EXCLUDED,
                    selectedChannelIds(event, EXCLUDED_CHANNELS));
                else throw new IllegalArgumentException("Section de configuration incompatible avec ce module");
            }
            case RESOURCES -> {
                logChannelId = selectedChannelId(event, LOG_CHANNEL);
                roles.put(ModuleResourcePurpose.PROTECTED, selectedRoleIds(event, PROTECTED_ROLES));
            }
            case TRIGGERS -> {
                if (module == ModuleType.AUTO_SANCTION_CHANNEL) channels.put(
                        ModuleResourcePurpose.WATCHED,
                        selectedChannelIds(event, WATCHED_CHANNELS));
                else if (module == ModuleType.AUTO_SANCTION_ROLE) roles.put(
                        ModuleResourcePurpose.WATCHED,
                        selectedRoleIds(event, WATCHED_ROLES));
                else throw new IllegalArgumentException("Section de déclencheurs incompatible avec ce module");
            }
            case RECENT_ACCOUNT -> settings = recentAccountSettings(event);
            case MENTION_SPAM -> settings = mentionSpamSettings(event);
            case SANCTION -> settings = sanctionSettings(event, module);
        }

        return new ModuleConfigurationSettings(logChannelId, roles, channels, settings);
    }

    private static void addRecentAccountSettings(Modal.Builder modal, ModuleConfiguration configuration) {
        ModerationSettings.AntiRaid settings = ModerationSettings.antiRaid(configuration);
        modal.addComponents(
                ruleModeSelector(settings.recentAccountEnabled(), settings.recentAccountAction()),
                numberInput(
                        ACCOUNT_AGE_DAYS,
                        "Âge maximal du compte (jours)",
                        "Les comptes plus récents déclenchent la règle.",
                        settings.recentAccountMaximumAgeDays(),
                        true),
                numberInput(
                        TIMEOUT_SECONDS,
                        "Durée d'exclusion (secondes)",
                        "Obligatoire uniquement pour une exclusion temporaire.",
                        Math.toIntExact(settings.recentAccountTimeout().toSeconds()),
                        false));
    }

    private static void addMentionSpamSettings(Modal.Builder modal, ModuleConfiguration configuration) {
        ModerationSettings.AntiRaid settings = ModerationSettings.antiRaid(configuration);
        modal.addComponents(
                ruleModeSelector(settings.mentionSpamEnabled(), settings.mentionAction()),
                numberInput(
                        MENTION_LIMIT,
                        "Nombre de mentions",
                        "Nombre cumulé qui déclenche la règle.",
                        settings.mentionLimit(),
                        true),
                numberInput(
                        MENTION_WINDOW_SECONDS,
                        "Fenêtre de détection (secondes)",
                        "Les mentions sont cumulées dans cette fenêtre.",
                        Math.toIntExact(settings.mentionWindow().toSeconds()),
                        true),
                numberInput(
                        TIMEOUT_SECONDS,
                        "Durée d'exclusion (secondes)",
                        "Obligatoire uniquement pour une exclusion temporaire.",
                        Math.toIntExact(settings.mentionTimeout().toSeconds()),
                        false),
                booleanSelector(
                        DELETE_MESSAGE,
                        "Suppression du message",
                        "Supprimer le message qui atteint le seuil.",
                        settings.deleteMentionMessage()));
    }

    private static void addSanctionSettings(Modal.Builder modal, ModuleConfiguration configuration) {
        ModerationSettings.AutomaticSanction settings = ModerationSettings.automaticSanction(configuration);
        modal.addComponents(
                actionSelector(settings.action().orElse(null)),
                numberInput(
                        TIMEOUT_SECONDS,
                        "Durée d'exclusion (secondes)",
                        "Obligatoire uniquement pour une exclusion temporaire.",
                        Math.toIntExact(settings.timeout().toSeconds()),
                        false),
                numberInput(
                        BAN_HISTORY_DAYS,
                        "Historique supprimé (jours)",
                        "De 0 à 7 jours lors d'un bannissement.",
                        settings.banHistoryDays(),
                        true));
        if (configuration.module() == ModuleType.AUTO_SANCTION_CHANNEL) {
            modal.addComponents(booleanSelector(
                    DELETE_MESSAGE,
                    "Suppression du message",
                    "Supprimer le message qui déclenche la règle.",
                    settings.deleteMessage()));
        }
    }

    private static Map<ModuleSetting, String> recentAccountSettings(ModalInteractionEvent event) {
        RuleMode mode = selectedRuleMode(event);
        int ageDays = positiveInt(event, ACCOUNT_AGE_DAYS, "L'âge maximal du compte");
        int timeoutSeconds = timeoutSeconds(event, mode.action());
        return ModerationSettings.recentAccountValues(mode.enabled(), ageDays, mode.action(), timeoutSeconds);
    }

    private static Map<ModuleSetting, String> mentionSpamSettings(ModalInteractionEvent event) {
        RuleMode mode = selectedRuleMode(event);
        int mentionLimit = positiveInt(event, MENTION_LIMIT, "Le nombre de mentions");
        int windowSeconds = positiveInt(event, MENTION_WINDOW_SECONDS, "La fenêtre de détection");
        int timeoutSeconds = timeoutSeconds(event, mode.action());
        boolean deleteMessage = Boolean.parseBoolean(selectedString(event, DELETE_MESSAGE));
        return ModerationSettings.mentionSpamValues(
                mode.enabled(),
                mentionLimit,
                windowSeconds,
                mode.action(),
                timeoutSeconds,
                deleteMessage);
    }

    private static Map<ModuleSetting, String> sanctionSettings(
            ModalInteractionEvent event,
            ModuleType module
    ) {
        SanctionAction action = SanctionAction.valueOf(selectedString(event, SANCTION_ACTION));
        int timeoutSeconds = timeoutSeconds(event, action);
        int historyDays = nonNegativeInt(event, BAN_HISTORY_DAYS, "La durée d'historique supprimé");
        if (historyDays > ModerationSettings.MAX_BAN_HISTORY_DAYS) {
            throw new IllegalArgumentException("L'historique supprimé est limité à 7 jours.");
        }
        boolean deleteMessage = module == ModuleType.AUTO_SANCTION_CHANNEL
                && Boolean.parseBoolean(selectedString(event, DELETE_MESSAGE));
        return ModerationSettings.automaticSanctionValues(action, timeoutSeconds, historyDays, deleteMessage);
    }

    private static int timeoutSeconds(ModalInteractionEvent event, SanctionAction action) {
        String raw = optionalString(event, TIMEOUT_SECONDS);
        if (raw.isBlank()) {
            if (action == SanctionAction.TIMEOUT) {
                throw new IllegalArgumentException("La durée d'exclusion est obligatoire pour cette action.");
            }
            return 600;
        }
        int seconds = parseInt(raw, "La durée d'exclusion");
        if (seconds <= 0 || seconds > ModerationSettings.MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("La durée d'exclusion doit être comprise entre 1 seconde et 28 jours.");
        }
        return seconds;
    }

    private static int positiveInt(ModalInteractionEvent event, String id, String label) {
        int value = parseInt(selectedString(event, id), label);
        if (value <= 0) {
            throw new IllegalArgumentException(label + " doit être positif.");
        }
        return value;
    }

    private static int nonNegativeInt(ModalInteractionEvent event, String id, String label) {
        int value = parseInt(selectedString(event, id), label);
        if (value < 0) {
            throw new IllegalArgumentException(label + " ne peut pas être négatif.");
        }
        return value;
    }

    private static int parseInt(String raw, String label) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " doit être un nombre entier.");
        }
    }

    private static RuleMode selectedRuleMode(ModalInteractionEvent event) {
        String raw = selectedString(event, RULE_MODE);
        if (raw.startsWith("DISABLED_")) {
            return new RuleMode(false, SanctionAction.valueOf(raw.substring("DISABLED_".length())));
        }
        return new RuleMode(true, SanctionAction.valueOf(raw));
    }

    private static Label ruleModeSelector(boolean enabled, SanctionAction action) {
        String disabledValue = "DISABLED_" + action.name();
        StringSelectMenu menu = actionOptions(StringSelectMenu.create(RULE_MODE))
                .addOption("Désactivée", disabledValue)
                .setRequiredRange(1, 1)
                .setDefaultValues(enabled ? action.name() : disabledValue)
                .build();
        return Label.of("État et action", "Activez la règle et choisissez son action.", menu);
    }

    private static Label actionSelector(SanctionAction current) {
        StringSelectMenu.Builder builder = actionOptions(StringSelectMenu.create(SANCTION_ACTION))
                .setRequiredRange(1, 1);
        if (current != null) {
            builder.setDefaultValues(current.name());
        }
        return Label.of(
                "Action",
                "Choisissez explicitement l'action appliquée.",
                builder.build());
    }

    private static StringSelectMenu.Builder actionOptions(StringSelectMenu.Builder builder) {
        return builder
                .addOption("Journalisation uniquement", SanctionAction.LOG_ONLY.name())
                .addOption("Exclusion temporaire", SanctionAction.TIMEOUT.name())
                .addOption("Expulsion", SanctionAction.KICK.name())
                .addOption("Bannissement", SanctionAction.BAN.name());
    }

    private static Label booleanSelector(
            String id,
            String title,
            String description,
            boolean current
    ) {
        StringSelectMenu menu = StringSelectMenu.create(id)
                .addOption("Oui", "true")
                .addOption("Non", "false")
                .setRequiredRange(1, 1)
                .setDefaultValues(String.valueOf(current))
                .build();
        return Label.of(title, description, menu);
    }

    private static Label numberInput(
            String id,
            String title,
            String description,
            int current,
            boolean required
    ) {
        TextInput input = TextInput.create(id, TextInputStyle.SHORT)
                .setRequired(required)
                .setMaxLength(10)
                .setValue(String.valueOf(current))
                .build();
        return Label.of(title, description, input);
    }

    private static String selectedString(ModalInteractionEvent event, String id) {
        String value = optionalString(event, id);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Un réglage obligatoire est manquant.");
        }
        return value;
    }

    private static String optionalString(ModalInteractionEvent event, String id) {
        ModalMapping value = event.getValue(id);
        return value == null ? "" : stringValue(value);
    }

    static String stringValue(ModalMapping value) {
        return switch (value.getType()) {
            case TEXT_INPUT -> {
                String text = value.getAsOptionalString();
                yield text == null ? "" : text;
            }
            case STRING_SELECT -> value.getAsStringList().stream().findFirst().orElse("");
            default -> throw new IllegalArgumentException(
                    "Le composant " + value.getCustomId() + " ne contient pas une valeur textuelle compatible.");
        };
    }

    private static Section defaultSection(ModuleType module) {
        return switch (module) {
            case ANTI_RAID, AUTO_SANCTION_CHANNEL, AUTO_SANCTION_ROLE -> Section.RESOURCES;
            default -> Section.DEFAULT;
        };
    }

    private static boolean supportsSection(ModuleType module, Section section) {
        return switch (section) {
            case DEFAULT -> module == ModuleType.AUTO_ROLE
                    || module == ModuleType.INTEGRATION_REMOVAL
                    || module == ModuleType.LINK_ENRICHMENT;
            case RESOURCES -> module == ModuleType.ANTI_RAID
                    || module == ModuleType.AUTO_SANCTION_CHANNEL
                    || module == ModuleType.AUTO_SANCTION_ROLE;
            case TRIGGERS -> module == ModuleType.AUTO_SANCTION_CHANNEL
                    || module == ModuleType.AUTO_SANCTION_ROLE;
            case RECENT_ACCOUNT, MENTION_SPAM -> module == ModuleType.ANTI_RAID;
            case SANCTION -> module == ModuleType.AUTO_SANCTION_CHANNEL
                    || module == ModuleType.AUTO_SANCTION_ROLE;
        };
    }

    private static String modalTitle(ModuleType module, Section section) {
        return switch (section) {
            case DEFAULT -> "Configurer " + label(module);
            case RESOURCES -> "Ressources de modération";
            case TRIGGERS -> "Configurer les déclencheurs";
            case RECENT_ACCOUNT -> "Règle des comptes récents";
            case MENTION_SPAM -> "Règle du spam de mentions";
            case SANCTION -> "Configurer l'action";
        };
    }

    public static String label(ModuleType module) {
        return switch (module) {
            case WARNINGS -> "Avertissements";
            case AUTO_ROLE -> "Rôle automatique";
            case MUSIC -> "Musique";
            case VOICE_RECORDING -> "Enregistrement vocal";
            case BOT_MESSAGES -> "Messages du bot";
            case LINK_ENRICHMENT -> "Enrichissement des liens";
            case INTEGRATION_REMOVAL -> "Suppression des intégrations";
            case ANTI_RAID -> "Anti-raid";
            case AUTO_SANCTION_CHANNEL -> "Sanction automatique par salon";
            case AUTO_SANCTION_ROLE -> "Sanction automatique par rôle";
        };
    }

    private static Label logChannelSelector(Guild guild, String currentId) {
        Set<String> current = currentId == null ? Set.of() : Set.of(currentId);
        return channelSelector(
                guild,
                LOG_CHANNEL,
                "Salon de logs",
                "Les actions et erreurs de ce module y seront publiées.",
                current,
                true,
                1);
    }

    private static Label protectedRolesSelector(Guild guild, ModuleConfiguration configuration) {
        return roleSelector(
                guild,
                PROTECTED_ROLES,
                "Rôles protégés (facultatif)",
                "Les membres possédant l'un de ces rôles seront ignorés.",
                configuration.roles(ModuleResourcePurpose.PROTECTED),
                false,
                MAX_SELECTIONS);
    }

    private static Label roleSelector(
            Guild guild,
            String id,
            String title,
            String description,
            Set<String> currentIds,
            boolean required,
            int maxSelections
    ) {
        EntitySelectMenu.Builder selector = EntitySelectMenu.create(id, SelectTarget.ROLE)
                .setPlaceholder(title)
                .setRequired(required)
                .setRequiredRange(required ? 1 : 0, maxSelections);
        setDefaults(selector, currentIds, guild::getRoleById, DefaultValue::from);
        return Label.of(title, description, selector.build());
    }

    private static Label channelSelector(
            Guild guild,
            String id,
            String title,
            String description,
            Set<String> currentIds,
            boolean required,
            int maxSelections
    ) {
        EntitySelectMenu.Builder selector = EntitySelectMenu.create(id, SelectTarget.CHANNEL)
                .setPlaceholder(title)
                .setChannelTypes(ConfigurationChannelTypes.guildMessageChannels())
                .setRequired(required)
                .setRequiredRange(required ? 1 : 0, maxSelections);
        setDefaults(selector, currentIds, guild::getGuildChannelById, DefaultValue::from);
        return Label.of(title, description, selector.build());
    }

    private static <T> void setDefaults(
            EntitySelectMenu.Builder selector,
            Set<String> currentIds,
            Function<String, T> resolver,
            Function<T, DefaultValue> defaultValue
    ) {
        List<DefaultValue> defaults = currentIds.stream()
                .map(resolver)
                .filter(value -> value != null)
                .map(defaultValue)
                .toList();
        if (!defaults.isEmpty()) {
            selector.setDefaultValues(defaults);
        }
    }

    private static Set<String> selectedRoleIds(ModalInteractionEvent event, String id) {
        ModalMapping value = event.getValue(id);
        if (value == null) {
            return Set.of();
        }
        return value.getAsMentions().getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> selectedChannelIds(ModalInteractionEvent event, String id) {
        ModalMapping value = event.getValue(id);
        if (value == null) {
            return Set.of();
        }
        return value.getAsMentions().getChannels().stream()
                .map(GuildChannel::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String selectedChannelId(ModalInteractionEvent event, String id) {
        return selectedChannelIds(event, id).stream().findFirst().orElse(null);
    }

    public enum SubmissionAction {
        ACTIVATE("activate"),
        CONFIGURE("configure");

        private final String id;

        SubmissionAction(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        private static SubmissionAction fromId(String id) {
            return Arrays.stream(values())
                    .filter(action -> action.id.equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Action de modale inconnue"));
        }
    }

    public enum Section {
        DEFAULT,
        RESOURCES,
        TRIGGERS,
        RECENT_ACCOUNT,
        MENTION_SPAM,
        SANCTION
    }

    private record RuleMode(boolean enabled, SanctionAction action) {
    }

    public record Submission(SubmissionAction action, ModuleType module, Section section) {
    }
}
