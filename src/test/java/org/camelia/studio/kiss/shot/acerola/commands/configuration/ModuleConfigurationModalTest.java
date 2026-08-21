package org.camelia.studio.kiss.shot.acerola.commands.configuration;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.modals.Modal;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                ModuleType.AUTO_SANCTION_ROLE), modules);
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
                    Map.of());

            Modal modal = ModuleConfigurationModal.create(
                    guild,
                    configuration,
                    ModuleConfigurationModal.SubmissionAction.ACTIVATE);

            assertTrue(modal.getComponents().size() >= 1);
            assertTrue(modal.getComponents().size() <= Modal.MAX_COMPONENTS);
        }
    }
}
