package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.AutoBanRoleService;
import org.camelia.studio.kiss.shot.acerola.services.moderation.AutomaticSanctionExecutor;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ModerationSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AutoBanRoleListener extends ModuleAwareListener {

    private final AutomaticSanctionExecutor sanctionExecutor = new AutomaticSanctionExecutor();

    public AutoBanRoleListener() {
        super(ModuleType.AUTO_SANCTION_ROLE);
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        var resolved = activeConfiguration(event.getGuild());
        if (resolved.isEmpty()) return;
        var configuration = resolved.get();
        Set<String> watchedRoleIds = configuration.roles(ModuleResourcePurpose.WATCHED);
        if (watchedRoleIds.isEmpty()) return;

        Member member = event.getMember();

        Set<String> addedRoleIds = event.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toUnmodifiableSet());
        Optional<String> matchedRoleId = new AutoBanRoleService(watchedRoleIds).findWatchedRoleId(addedRoleIds);
        if (matchedRoleId.isEmpty()) return;

        Role matchedRole = event.getGuild().getRoleById(matchedRoleId.get());
        String roleLabel = matchedRole == null
                ? matchedRoleId.get()
                : matchedRole.getName() + " (" + matchedRole.getId() + ")";
        ModerationSettings.AutomaticSanction settings = ModerationSettings.automaticSanction(configuration);
        settings.action().ifPresent(action -> sanctionExecutor.execute(
                member,
                null,
                configuration,
                action,
                settings.timeout(),
                settings.banHistoryDays(),
                false,
                "Obtention d'un rôle déclencheur",
                "Rôle : " + roleLabel));
    }

}
