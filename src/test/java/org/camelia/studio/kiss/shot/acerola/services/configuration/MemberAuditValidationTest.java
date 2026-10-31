package org.camelia.studio.kiss.shot.acerola.services.configuration;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ApiDefaults;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberAuditValidationTest {
    private final ModuleConfigurationValidator validator = new ModuleConfigurationValidator((guild, id) -> Optional.empty());

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

    @Test
    void anArchivedLogThreadIsStillValid() {
        ModuleConfigurationValidator archived = new ModuleConfigurationValidator(
                (guild, id) -> Optional.of(thread(true, false)));

        assertTrue(archived.validate(guild(), configuration(null, Set.of("1"))).valid());
    }

    @Test
    void aLockedLogThreadIsRejected() {
        ModuleConfigurationValidator locked = new ModuleConfigurationValidator(
                (guild, id) -> Optional.of(thread(true, true)));

        assertEquals(
                "Le fil de logs est verrouillé",
                locked.validate(guild(), configuration(null, Set.of("1"))).reason());
    }

    private static ThreadChannel thread(boolean archived, boolean locked) {
        return (ThreadChannel) Proxy.newProxyInstance(
                ThreadChannel.class.getClassLoader(),
                new Class<?>[]{ThreadChannel.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isArchived" -> archived;
                    case "isLocked" -> locked;
                    case "getType" -> ChannelType.GUILD_PUBLIC_THREAD;
                    default -> null;
                });
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
        SelfMember self = (SelfMember) Proxy.newProxyInstance(
                SelfMember.class.getClassLoader(),
                new Class<?>[]{SelfMember.class},
                (proxy, method, arguments) -> method.getName().equals("hasPermission") ? true : null);
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[]{Guild.class},
                (proxy, method, arguments) -> method.getName().equals("getSelfMember") ? self : null);
    }
}
