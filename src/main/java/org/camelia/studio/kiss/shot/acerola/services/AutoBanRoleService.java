package org.camelia.studio.kiss.shot.acerola.services;

import java.util.Optional;
import java.util.Set;

public class AutoBanRoleService {

    private final Set<String> watchedRoleIds;

    public AutoBanRoleService(Set<String> watchedRoleIds) {
        this.watchedRoleIds = watchedRoleIds == null ? Set.of() : Set.copyOf(watchedRoleIds);
    }

    public Optional<String> findWatchedRoleId(Set<String> assignedRoleIds) {
        if (assignedRoleIds == null || assignedRoleIds.isEmpty() || watchedRoleIds.isEmpty()) {
            return Optional.empty();
        }

        return assignedRoleIds.stream()
                .filter(watchedRoleIds::contains)
                .findFirst();
    }
}
