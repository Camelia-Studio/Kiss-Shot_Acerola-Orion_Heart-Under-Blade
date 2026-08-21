package org.camelia.studio.kiss.shot.acerola.services.configuration;

import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleConfigurationTest {
    @Test
    void changingAResourceKeepsTheOtherSettingsAndDoesNotMutateTheOriginal() {
        ModuleConfiguration original = new ModuleConfiguration(
                ModuleType.AUTO_ROLE,
                ModuleStatus.DISABLED,
                null,
                "100",
                Map.of(ModuleResourcePurpose.PROTECTED, Set.of("200")),
                Map.of(ModuleResourcePurpose.EXCLUDED, Set.of("300")));

        ModuleConfiguration changed = original.withRoles(ModuleResourcePurpose.TARGET, Set.of("201"));

        assertEquals(Set.of(), original.roles(ModuleResourcePurpose.TARGET));
        assertEquals(Set.of("201"), changed.roles(ModuleResourcePurpose.TARGET));
        assertEquals(Set.of("200"), changed.roles(ModuleResourcePurpose.PROTECTED));
        assertEquals(Set.of("300"), changed.channels(ModuleResourcePurpose.EXCLUDED));
        assertEquals("100", changed.logChannelId());
    }

    @Test
    void resourceCollectionsAreImmutable() {
        ModuleConfiguration configuration = new ModuleConfiguration(
                ModuleType.MUSIC,
                ModuleStatus.DISABLED,
                null,
                null,
                Map.of(ModuleResourcePurpose.PROTECTED, Set.of("200")),
                Map.of());

        assertThrows(
                UnsupportedOperationException.class,
                () -> configuration.roles(ModuleResourcePurpose.PROTECTED).add("201"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> configuration.roleIds().put(ModuleResourcePurpose.TARGET, Set.of("201")));
    }

    @Test
    void firstResourceCanBeAddedToAnEmptyConfiguration() {
        ModuleConfiguration configuration = new ModuleConfiguration(
                ModuleType.AUTO_ROLE,
                ModuleStatus.DISABLED,
                null,
                null,
                Map.of(),
                Map.of());

        ModuleConfiguration changed = configuration.withRoles(
                ModuleResourcePurpose.TARGET,
                Set.of("201"));

        assertEquals(Set.of("201"), changed.roles(ModuleResourcePurpose.TARGET));
    }
}
