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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleActivationModalTest {
    @Test
    void onlyModulesWithRequiredResourcesOpenAModal() {
        Set<ModuleType> modules = Stream.of(ModuleType.values())
                .filter(ModuleActivationModal::requiresConfiguration)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(Set.of(
                ModuleType.AUTO_ROLE,
                ModuleType.INTEGRATION_REMOVAL,
                ModuleType.ANTI_RAID,
                ModuleType.AUTO_SANCTION_CHANNEL,
                ModuleType.AUTO_SANCTION_ROLE), modules);
    }

    @Test
    void modalIdResolvesItsConfiguredModule() {
        assertEquals(
                ModuleType.AUTO_ROLE,
                ModuleActivationModal.moduleFrom("config:activate:AUTO_ROLE").orElseThrow());
        assertTrue(ModuleActivationModal.moduleFrom("config:activate:MUSIC").isEmpty());
        assertTrue(ModuleActivationModal.moduleFrom("another-modal").isEmpty());
    }

    @Test
    void everyRequiredConfigurationBuildsAValidJdaModal() {
        Guild guild = (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[]{Guild.class},
                (proxy, method, arguments) -> null);

        for (ModuleType module : ModuleType.values()) {
            if (!ModuleActivationModal.requiresConfiguration(module)) {
                continue;
            }
            ModuleConfiguration configuration = new ModuleConfiguration(
                    module,
                    ModuleStatus.DISABLED,
                    null,
                    null,
                    Map.of(),
                    Map.of());

            Modal modal = ModuleActivationModal.create(guild, configuration);

            assertTrue(modal.getComponents().size() >= 1);
            assertTrue(modal.getComponents().size() <= Modal.MAX_COMPONENTS);
        }
    }
}
