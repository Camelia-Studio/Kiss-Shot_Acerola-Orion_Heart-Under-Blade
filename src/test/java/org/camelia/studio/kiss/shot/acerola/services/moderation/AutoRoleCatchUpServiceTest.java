package org.camelia.studio.kiss.shot.acerola.services.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRoleCatchUpServiceTest {
    @Test
    void catchUpTargetsOnlyHumansMissingTheRole() {
        assertTrue(AutoRoleCatchUpService.shouldAssign(member(false, List.of()), "role-1"));
        assertFalse(AutoRoleCatchUpService.shouldAssign(member(true, List.of()), "role-1"));
        assertFalse(AutoRoleCatchUpService.shouldAssign(member(false, List.of(role("role-1"))), "role-1"));
    }

    private Member member(boolean bot, List<Role> roles) {
        User user = (User) Proxy.newProxyInstance(
                User.class.getClassLoader(),
                new Class<?>[]{User.class},
                (proxy, method, arguments) -> method.getName().equals("isBot") && bot);
        return (Member) Proxy.newProxyInstance(
                Member.class.getClassLoader(),
                new Class<?>[]{Member.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUser" -> user;
                    case "getRoles" -> roles;
                    default -> null;
                });
    }

    private Role role(String id) {
        return (Role) Proxy.newProxyInstance(
                Role.class.getClassLoader(),
                new Class<?>[]{Role.class},
                (proxy, method, arguments) -> method.getName().equals("getId") ? id : null);
    }
}
