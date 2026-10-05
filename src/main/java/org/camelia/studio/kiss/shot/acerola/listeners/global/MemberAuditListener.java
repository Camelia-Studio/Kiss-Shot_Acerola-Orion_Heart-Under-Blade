package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateGlobalNameEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.moderation.MemberAuditService;
import org.camelia.studio.kiss.shot.acerola.services.moderation.MemberAuditService.Kind;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ModerationSettings;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class MemberAuditListener extends ModuleAwareListener {
    private final MemberAuditService auditService = new MemberAuditService();

    public MemberAuditListener() {
        super(ModuleType.MEMBER_AUDIT);
    }

    @Override
    public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
        recordName(
                event.getGuild(),
                event.getUser(),
                Kind.GUILD_NICKNAME,
                event.getOldNickname(),
                event.getNewNickname());
    }

    @Override
    public void onUserUpdateGlobalName(@NotNull UserUpdateGlobalNameEvent event) {
        recordNameInMutualGuilds(event.getUser(), Kind.GLOBAL_NAME, event.getOldGlobalName(), event.getNewGlobalName());
    }

    @Override
    public void onUserUpdateName(@NotNull UserUpdateNameEvent event) {
        recordNameInMutualGuilds(event.getUser(), Kind.USERNAME, event.getOldName(), event.getNewName());
    }

    @Override
    public void onGuildMemberUpdateAvatar(@NotNull GuildMemberUpdateAvatarEvent event) {
        recordAvatar(
                event.getGuild(),
                event.getUser(),
                Kind.GUILD_AVATAR,
                event.getOldAvatarUrl(),
                event.getNewAvatarUrl());
    }

    @Override
    public void onUserUpdateAvatar(@NotNull UserUpdateAvatarEvent event) {
        User user = event.getUser();
        for (Guild guild : user.getMutualGuilds()) {
            recordAvatar(guild, user, Kind.GLOBAL_AVATAR, event.getOldAvatarUrl(), event.getNewAvatarUrl());
        }
    }

    private void recordNameInMutualGuilds(User user, Kind kind, String oldValue, String newValue) {
        for (Guild guild : user.getMutualGuilds()) {
            recordName(guild, user, kind, oldValue, newValue);
        }
    }

    private void recordName(Guild guild, User user, Kind kind, String oldValue, String newValue) {
        record(guild, user, ModerationSettings.MemberAudit::watchNickname, configuration ->
                auditService.recordNameChange(guild, configuration, user, kind, oldValue, newValue));
    }

    private void recordAvatar(Guild guild, User user, Kind kind, String oldUrl, String newUrl) {
        record(guild, user, ModerationSettings.MemberAudit::watchAvatar, configuration ->
                auditService.recordAvatarChange(guild, configuration, user, kind, oldUrl, newUrl));
    }

    private void record(
            Guild guild,
            User user,
            Predicate<ModerationSettings.MemberAudit> watched,
            Consumer<ModuleConfiguration> action
    ) {
        // Un bot ne se fait pas passer pour un membre : ses changements de profil ne sont pas audités.
        if (user.isBot()) return;
        activeConfiguration(guild)
                .filter(configuration -> watched.test(ModerationSettings.memberAudit(configuration)))
                .ifPresent(action);
    }
}
