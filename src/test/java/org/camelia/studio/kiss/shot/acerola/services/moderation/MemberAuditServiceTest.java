package org.camelia.studio.kiss.shot.acerola.services.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.camelia.studio.kiss.shot.acerola.services.moderation.MemberAuditService.Kind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberAuditServiceTest {
    @Test
    void nameChangeShowsBothValuesAndTheAccountContext() {
        MessageEmbed embed = MemberAuditService.embed(member(), user(), Kind.GUILD_NICKNAME, "Camille", "Admin");

        assertEquals("Audit des membres — Pseudo du serveur", embed.getTitle());
        assertEquals("Camille", field(embed, "Ancienne valeur"));
        assertEquals("Admin", field(embed, "Nouvelle valeur"));
        assertTrue(field(embed, "Utilisateur").contains("123456789012345678"));
        assertTrue(embed.getFields().stream().anyMatch(field -> "Compte créé".equals(field.getName())));
        assertTrue(embed.getFields().stream().anyMatch(field -> "Arrivé sur le serveur".equals(field.getName())));
    }

    @Test
    void removedNicknameIsExplicit() {
        MessageEmbed embed = MemberAuditService.embed(member(), user(), Kind.GUILD_NICKNAME, "Camille", null);

        assertEquals("*Aucun*", field(embed, "Nouvelle valeur"));
    }

    @Test
    void impersonatedNamesCannotInjectMarkdown() {
        MessageEmbed embed = MemberAuditService.embed(
                member(), user(), Kind.GLOBAL_NAME, "Camille", "**Modérateur** [lien](https://example.org)");

        String value = field(embed, "Nouvelle valeur");
        assertTrue(value.contains("\\*\\*Modérateur\\*\\*"), value);
        assertTrue(value.contains("\\["), value);
    }

    @Test
    void avatarChangeShowsThePreviousAvatarAsThumbnailAndTheNewOneAsImage() {
        MessageEmbed embed = MemberAuditService.embed(
                member(),
                user(),
                Kind.GLOBAL_AVATAR,
                "https://cdn.discordapp.com/avatars/1/old.png",
                "https://cdn.discordapp.com/avatars/1/new.png");

        assertEquals("https://cdn.discordapp.com/avatars/1/old.png", embed.getThumbnail().getUrl());
        assertEquals("https://cdn.discordapp.com/avatars/1/new.png", embed.getImage().getUrl());
    }

    @Test
    void defaultAvatarHasNoImage() {
        MessageEmbed embed = MemberAuditService.embed(null, user(), Kind.GUILD_AVATAR, null, null);

        assertNull(embed.getThumbnail());
        assertNull(embed.getImage());
        assertEquals("*Avatar par défaut*", field(embed, "Nouvel avatar"));
        assertTrue(embed.getFields().stream().noneMatch(field -> "Arrivé sur le serveur".equals(field.getName())));
    }

    @Test
    void veryLongValuesStayWithinDiscordsFieldLimit() {
        MessageEmbed embed = MemberAuditService.embed(member(), user(), Kind.USERNAME, "a", "b".repeat(5000));

        assertEquals(1024, field(embed, "Nouvelle valeur").length());
    }

    private static String field(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    private static User user() {
        return (User) Proxy.newProxyInstance(
                User.class.getClassLoader(),
                new Class<?>[]{User.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAsMention" -> "<@123456789012345678>";
                    case "getName" -> "camille";
                    case "getId" -> "123456789012345678";
                    case "getTimeCreated" -> OffsetDateTime.parse("2024-01-01T10:00:00Z");
                    default -> null;
                });
    }

    private static Member member() {
        return (Member) Proxy.newProxyInstance(
                Member.class.getClassLoader(),
                new Class<?>[]{Member.class},
                (proxy, method, arguments) -> "getTimeJoined".equals(method.getName())
                        ? OffsetDateTime.parse("2025-06-01T10:00:00Z")
                        : null);
    }
}
