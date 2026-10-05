package org.camelia.studio.kiss.shot.acerola.services.configuration;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ApiDefaults;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MemberAuditValidationTest {
    private final ModuleConfigurationValidator validator = new ModuleConfigurationValidator();

    @Test
    void theServerLogChannelDoesNotReplaceTheModuleLogChannel() {
        ModuleConfiguration configuration = configuration("123456789012345678", Set.of());

        ModuleValidationResult result = validator.validate(guild(), configuration);

        assertFalse(result.valid());
        assertEquals("Un salon de logs doit être configuré pour ce module", result.reason());
    }

    @Test
    void aSingleModuleLogChannelIsRequired() {
        ModuleConfiguration configuration = configuration(null, Set.of("1", "2"));

        assertEquals(
                "Un salon de logs doit être configuré pour ce module",
                validator.validate(guild(), configuration).reason());
    }

    @Test
    void theModuleLogChannelMustStillExist() {
        ModuleConfiguration configuration = configuration(null, Set.of("1"));

        assertEquals("Le salon de logs n'existe plus", validator.validate(guild(), configuration).reason());
    }

    private static ModuleConfiguration configuration(String serverLogChannelId, Set<String> moduleLogChannels) {
        return new ModuleConfiguration(
                ModuleType.MEMBER_AUDIT,
                ModuleStatus.DISABLED,
                null,
                serverLogChannelId,
                Map.of(),
                Map.of(ModuleResourcePurpose.LOG, moduleLogChannels),
                ApiDefaults.settings(ModuleType.MEMBER_AUDIT));
    }

    private static Guild guild() {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[]{Guild.class},
                (proxy, method, arguments) -> null);
    }
}
