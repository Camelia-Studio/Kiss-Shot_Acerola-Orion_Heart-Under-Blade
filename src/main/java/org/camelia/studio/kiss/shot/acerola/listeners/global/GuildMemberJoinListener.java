package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;

public class GuildMemberJoinListener extends ModuleAwareListener {
    public GuildMemberJoinListener() {
        super(ModuleType.AUTO_ROLE);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        if (member.getUser().isBot()) {
            return;
        }

        Role role = activeConfiguration(event.getGuild())
                .flatMap(configuration -> configuration.roles(ModuleResourcePurpose.TARGET).stream().findFirst())
                .map(event.getGuild()::getRoleById)
                .orElse(null);

        if (role != null) {
            event.getGuild().addRoleToMember(member, role).queue();
        }
    }
}
