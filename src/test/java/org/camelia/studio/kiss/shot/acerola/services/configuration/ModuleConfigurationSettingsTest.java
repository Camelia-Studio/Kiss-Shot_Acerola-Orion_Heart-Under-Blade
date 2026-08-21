package org.camelia.studio.kiss.shot.acerola.services.configuration;

import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleConfigurationSettingsTest {
    @Test
    void emptySettingsKeepTheCurrentConfiguration() {
        ModuleConfiguration current = configuration();

        ModuleConfiguration candidate = ModuleConfigurationSettings.empty().applyTo(current);

        assertEquals(current, candidate);
    }

    @Test
    void selectedFieldsReplaceTheirResourcesAndKeepUnrelatedSettings() {
        ModuleConfiguration current = configuration();
        ModuleConfigurationSettings settings = new ModuleConfigurationSettings(
                "new-log",
                Map.of(
                        ModuleResourcePurpose.WATCHED, Set.of("watched-role"),
                        ModuleResourcePurpose.PROTECTED, Set.of()),
                Map.of());

        ModuleConfiguration candidate = settings.applyTo(current);

        assertEquals("new-log", candidate.logChannelId());
        assertEquals(Set.of("watched-role"), candidate.roles(ModuleResourcePurpose.WATCHED));
        assertEquals(Set.of(), candidate.roles(ModuleResourcePurpose.PROTECTED));
        assertEquals(Set.of("excluded-channel"), candidate.channels(ModuleResourcePurpose.EXCLUDED));
    }

    private ModuleConfiguration configuration() {
        return new ModuleConfiguration(
                ModuleType.AUTO_SANCTION_ROLE,
                ModuleStatus.DISABLED,
                null,
                "old-log",
                Map.of(ModuleResourcePurpose.PROTECTED, Set.of("protected-role")),
                Map.of(ModuleResourcePurpose.EXCLUDED, Set.of("excluded-channel")));
    }
}
