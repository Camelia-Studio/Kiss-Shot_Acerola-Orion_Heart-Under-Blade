package org.camelia.studio.kiss.shot.acerola.services.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.util.Set;

public final class ModerationProtectionService {
    private ModerationProtectionService() {
    }

    public static boolean isProtected(Member member, Set<String> protectedRoleIds) {
        if (member.getUser().isBot() || member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        return member.getRoles().stream().anyMatch(role -> protectedRoleIds.contains(role.getId()));
    }
}
