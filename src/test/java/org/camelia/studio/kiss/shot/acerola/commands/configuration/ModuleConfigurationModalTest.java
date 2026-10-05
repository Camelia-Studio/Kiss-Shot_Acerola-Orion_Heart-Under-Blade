package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ApiDefaults;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleConfigurationModalTest {
    @Test
    void onlyModulesWithRequiredResourcesOpenAModal() {
        Set<ModuleType> modules = Stream.of(ModuleType.values())
                .filter(ModuleConfigurationModal::requiresConfigurationForActivation)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(Set.of(
                ModuleType.AUTO_ROLE,
                ModuleType.INTEGRATION_REMOVAL,
                ModuleType.ANTI_RAID,
                ModuleType.AUTO_SANCTION_CHANNEL,
                ModuleType.AUTO_SANCTION_ROLE,
                ModuleType.MEMBER_AUDIT), modules);
    }

    @Test
    void memberAuditModalsExposeTheWatchedItemsAndOnlyTheLogChannel() {
        Guild guild = emptyGuild();
        ModuleConfiguration memberAudit = configuration(ModuleType.MEMBER_AUDIT);

        Modal watched = ModuleConfigurationModal.create(
                guild,
                memberAudit,
                ModuleConfigurationModal.SubmissionAction.CONFIGURE,
                ModuleConfigurationModal.Section.DEFAULT);
        Modal resources = ModuleConfigurationModal.create(
                guild,
                memberAudit,
                ModuleConfigurationModal.SubmissionAction.ACTIVATE);

        assertEquals(2, watched.getComponents().size());
        assertEquals(1, resources.getComponents().size());
        assertTrue(ModuleConfigurationModal.submissionFrom("config:configure:MEMBER_AUDIT:DEFAULT").isPresent());
        assertTrue(ModuleConfigurationModal.submissionFrom("config:configure:MEMBER_AUDIT:RESOURCES").isPresent());
        assertTrue(ModuleConfigurationModal.submissionFrom("config:configure:MEMBER_AUDIT:SANCTION").isEmpty());
    }

    @Test
    void optionalLinkSettingsAreEditableWithoutBlockingActivation() {
        assertTrue(ModuleConfigurationModal.hasEditableSettings(ModuleType.LINK_ENRICHMENT));
        assertFalse(ModuleConfigurationModal.requiresConfigurationForActivation(ModuleType.LINK_ENRICHMENT));
    }

    @Test
    void modalIdResolvesItsConfiguredModule() {
        assertEquals(
                ModuleType.AUTO_ROLE,
                ModuleConfigurationModal.submissionFrom("config:activate:AUTO_ROLE")
                        .orElseThrow()
                        .module());
        assertEquals(
                ModuleConfigurationModal.SubmissionAction.CONFIGURE,
                ModuleConfigurationModal.submissionFrom("config:configure:AUTO_ROLE")
                        .orElseThrow()
                        .action());
        assertTrue(ModuleConfigurationModal.submissionFrom("config:activate:MUSIC").isEmpty());
        assertTrue(ModuleConfigurationModal.submissionFrom("another-modal").isEmpty());
    }

    @Test
    void everyRequiredConfigurationBuildsAValidJdaModal() {
        Guild guild = (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[]{Guild.class},
                (proxy, method, arguments) -> null);

        for (ModuleType module : ModuleType.values()) {
            if (!ModuleConfigurationModal.hasEditableSettings(module)) {
                continue;
            }
            ModuleConfiguration configuration = new ModuleConfiguration(
                    module,
                    ModuleStatus.DISABLED,
                    null,
                    null,
                    Map.of(),
                    Map.of(),
                    ApiDefaults.settings(module));

            Modal modal = ModuleConfigurationModal.create(
                    guild,
                    configuration,
                    ModuleConfigurationModal.SubmissionAction.ACTIVATE);

            assertTrue(modal.getComponents().size() >= 1);
            assertTrue(modal.getComponents().size() <= Modal.MAX_COMPONENTS);
        }
    }

    @Test
    void optionalSelectorsAreExplicitlyMarkedAsOptional() {
        Guild guild = emptyGuild();

        assertSelectorRequirement(createModal(guild, ModuleType.LINK_ENRICHMENT), 0, false);
        assertSelectorRequirement(createModal(guild, ModuleType.ANTI_RAID), 1, false);
        assertSelectorRequirement(createModal(guild, ModuleType.AUTO_SANCTION_CHANNEL), 1, false);
        assertSelectorRequirement(createModal(guild, ModuleType.AUTO_SANCTION_ROLE), 1, false);
    }

    @Test
    void mandatorySelectorsAreExplicitlyMarkedAsRequired() {
        Guild guild = emptyGuild();

        assertSelectorRequirement(createModal(guild, ModuleType.AUTO_ROLE), 0, true);
        assertSelectorRequirement(createModal(guild, ModuleType.INTEGRATION_REMOVAL), 0, true);
        assertSelectorRequirement(createModal(guild, ModuleType.ANTI_RAID), 0, true);
    }

    @Test
    void channelSelectorsUseEveryMessageCapableGuildChannel() {
        EntitySelectMenu selector = selectorAt(createModal(emptyGuild(), ModuleType.LINK_ENRICHMENT), 0);

        assertEquals(ConfigurationChannelTypes.guildMessageChannels(), selector.getChannelTypes());
    }

    @Test
    void targetedModerationModalsRespectDiscordsFiveComponentLimit() {
        Guild guild = emptyGuild();
        ModuleConfiguration antiRaid = configuration(ModuleType.ANTI_RAID);
        ModuleConfiguration channelSanction = configuration(ModuleType.AUTO_SANCTION_CHANNEL);

        for (ModuleConfigurationModal.Section section : List.of(
                ModuleConfigurationModal.Section.RECENT_ACCOUNT,
                ModuleConfigurationModal.Section.MENTION_SPAM,
                ModuleConfigurationModal.Section.RESOURCES)) {
            Modal modal = ModuleConfigurationModal.create(
                    guild,
                    antiRaid,
                    ModuleConfigurationModal.SubmissionAction.CONFIGURE,
                    section);
            assertTrue(modal.getComponents().size() <= Modal.MAX_COMPONENTS);
        }
        for (ModuleConfigurationModal.Section section : List.of(
                ModuleConfigurationModal.Section.TRIGGERS,
                ModuleConfigurationModal.Section.SANCTION,
                ModuleConfigurationModal.Section.RESOURCES)) {
            Modal modal = ModuleConfigurationModal.create(
                    guild,
                    channelSanction,
                    ModuleConfigurationModal.SubmissionAction.CONFIGURE,
                    section);
            assertTrue(modal.getComponents().size() <= Modal.MAX_COMPONENTS);
        }
    }

    @Test
    void readsTextInputsAndStringSelectsWithTheirJdaSpecificAccessors() {
        ModalMapping textInput = mapping(
                "timeout_seconds",
                Component.Type.TEXT_INPUT,
                DataObject.empty().put("value", "600"));
        ModalMapping stringSelect = mapping(
                "sanction_action",
                Component.Type.STRING_SELECT,
                DataObject.empty().put("values", DataArray.fromCollection(List.of("BAN"))));

        assertEquals("600", ModuleConfigurationModal.stringValue(textInput));
        assertEquals("BAN", ModuleConfigurationModal.stringValue(stringSelect));
    }

    private static Modal createModal(Guild guild, ModuleType module) {
        ModuleConfiguration configuration = configuration(module);
        return ModuleConfigurationModal.create(
                guild,
                configuration,
                ModuleConfigurationModal.SubmissionAction.ACTIVATE);
    }

    private static ModuleConfiguration configuration(ModuleType module) {
        return new ModuleConfiguration(
                module,
                ModuleStatus.DISABLED,
                null,
                null,
                Map.of(),
                Map.of(),
                ApiDefaults.settings(module));
    }

    private static Guild emptyGuild() {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[]{Guild.class},
                (proxy, method, arguments) -> null);
    }

    private static ModalMapping mapping(String id, Component.Type type, DataObject values) {
        values.put("id", 1)
                .put("custom_id", id)
                .put("type", type.getKey());
        return new ModalMapping(null, DataObject.empty(), values);
    }

    private static void assertSelectorRequirement(Modal modal, int componentIndex, boolean required) {
        EntitySelectMenu selector = selectorAt(modal, componentIndex);

        assertEquals(required, selector.isRequired());
        assertEquals(required ? 1 : 0, selector.getMinValues());
    }

    private static EntitySelectMenu selectorAt(Modal modal, int componentIndex) {
        Label label = assertInstanceOf(Label.class, modal.getComponents().get(componentIndex));
        return assertInstanceOf(EntitySelectMenu.class, label.getChild());
    }
}
