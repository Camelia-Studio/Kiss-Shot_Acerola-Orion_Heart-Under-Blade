package org.camelia.studio.kiss.shot.acerola.services;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoBanRoleServiceTest {

    @Test
    void detectsAssignedWatchedRole() {
        AutoBanRoleService service = new AutoBanRoleService(Set.of("role-1", "role-2"));

        Optional<String> matchedRoleId = service.findWatchedRoleId(Set.of("role-3", "role-2"));

        assertTrue(matchedRoleId.isPresent());
        assertEquals("role-2", matchedRoleId.get());
    }

    @Test
    void ignoresUnwatchedAssignedRoles() {
        AutoBanRoleService service = new AutoBanRoleService(Set.of("role-1", "role-2"));

        Optional<String> matchedRoleId = service.findWatchedRoleId(Set.of("role-3", "role-4"));

        assertTrue(matchedRoleId.isEmpty());
    }
}
