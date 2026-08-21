package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationSettings;

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
        ModuleType module = configuration.module();
        Modal.Builder modal = Modal.create(
                MODAL_PREFIX + action.id() + ":" + module.name(),
                "Configurer " + label(module));

        switch (module) {
            case AUTO_ROLE -> modal.addComponents(roleSelector(
                    guild,
                    TARGET_ROLE,
                    "Rôle à attribuer",
                    "Choisissez le rôle attribué aux nouveaux membres.",
                    configuration.roles(ModuleResourcePurpose.TARGET),
                    true,
                    1));
            case INTEGRATION_REMOVAL -> modal.addComponents(channelSelector(
                    guild,
                    WATCHED_CHANNELS,
                    "Salons surveillés",
                    "Choisissez les salons où les intégrations seront supprimées.",
                    configuration.channels(ModuleResourcePurpose.WATCHED),
                    true,
                    MAX_SELECTIONS));
            case LINK_ENRICHMENT -> modal.addComponents(channelSelector(
                    guild,
                    EXCLUDED_CHANNELS,
                    "Salons exclus (facultatif)",
                    "Les liens publiés dans ces salons ne seront pas enrichis.",
                    configuration.channels(ModuleResourcePurpose.EXCLUDED),
                    false,
                    MAX_SELECTIONS));
            case ANTI_RAID -> modal.addComponents(
                    logChannelSelector(guild, configuration.logChannelId()),
                    protectedRolesSelector(guild, configuration));
            case AUTO_SANCTION_CHANNEL -> modal.addComponents(
                    logChannelSelector(guild, configuration.logChannelId()),
                    channelSelector(
                            guild,
                            WATCHED_CHANNELS,
                            "Salons déclencheurs",
                            "Un message dans l'un de ces salons déclenche la sanction.",
                            configuration.channels(ModuleResourcePurpose.WATCHED),
                            true,
                            MAX_SELECTIONS),
                    protectedRolesSelector(guild, configuration));
            case AUTO_SANCTION_ROLE -> modal.addComponents(
                    logChannelSelector(guild, configuration.logChannelId()),
                    roleSelector(
                            guild,
                            WATCHED_ROLES,
                            "Rôles déclencheurs",
                            "L'obtention de l'un de ces rôles déclenche la sanction.",
                            configuration.roles(ModuleResourcePurpose.WATCHED),
                            true,
                            MAX_SELECTIONS),
                    protectedRolesSelector(guild, configuration));
            default -> throw new IllegalArgumentException("Ce module ne nécessite aucune modale de configuration");
        }

        return modal.build();
    }

    public static Optional<Submission> submissionFrom(String modalId) {
        if (modalId == null || !modalId.startsWith(MODAL_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = modalId.substring(MODAL_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            SubmissionAction action = SubmissionAction.fromId(parts[0]);
            ModuleType module = ModuleType.valueOf(parts[1]);
            return hasEditableSettings(module)
                    ? Optional.of(new Submission(action, module))
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static ModuleConfigurationSettings settingsFrom(ModalInteractionEvent event, ModuleType module) {
        Map<ModuleResourcePurpose, Set<String>> roles = new EnumMap<>(ModuleResourcePurpose.class);
        Map<ModuleResourcePurpose, Set<String>> channels = new EnumMap<>(ModuleResourcePurpose.class);
        String logChannelId = null;

        switch (module) {
            case AUTO_ROLE -> roles.put(ModuleResourcePurpose.TARGET, selectedRoleIds(event, TARGET_ROLE));
            case INTEGRATION_REMOVAL -> channels.put(
                    ModuleResourcePurpose.WATCHED,
                    selectedChannelIds(event, WATCHED_CHANNELS));
            case LINK_ENRICHMENT -> channels.put(
                    ModuleResourcePurpose.EXCLUDED,
                    selectedChannelIds(event, EXCLUDED_CHANNELS));
            case ANTI_RAID -> {
                logChannelId = selectedChannelId(event, LOG_CHANNEL);
                roles.put(ModuleResourcePurpose.PROTECTED, selectedRoleIds(event, PROTECTED_ROLES));
            }
            case AUTO_SANCTION_CHANNEL -> {
                logChannelId = selectedChannelId(event, LOG_CHANNEL);
                channels.put(ModuleResourcePurpose.WATCHED, selectedChannelIds(event, WATCHED_CHANNELS));
                roles.put(ModuleResourcePurpose.PROTECTED, selectedRoleIds(event, PROTECTED_ROLES));
            }
            case AUTO_SANCTION_ROLE -> {
                logChannelId = selectedChannelId(event, LOG_CHANNEL);
                roles.put(ModuleResourcePurpose.WATCHED, selectedRoleIds(event, WATCHED_ROLES));
                roles.put(ModuleResourcePurpose.PROTECTED, selectedRoleIds(event, PROTECTED_ROLES));
            }
            default -> throw new IllegalArgumentException("Ce module ne nécessite aucune configuration");
        }

        return new ModuleConfigurationSettings(logChannelId, roles, channels);
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

    public record Submission(SubmissionAction action, ModuleType module) {
    }
}
