package org.camelia.studio.kiss.shot.acerola.commands.moderation;

import net.dv8tion.jda.api.Permission;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationCommandPermissionTest {
    @Test
    void warningCommandsEnforceModerateMembersAtRuntime() {
        assertEquals(Set.of(Permission.MODERATE_MEMBERS), new AvertoCommand().requiredPermissions());
        assertEquals(Set.of(Permission.MODERATE_MEMBERS), new AvertoListCommand().requiredPermissions());
    }
}
