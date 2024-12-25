package org.camelia.studio.kiss.shot.acerola.listeners.global;

import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildMemberJoinListener extends ListenerAdapter {
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();

        Role role = event.getGuild().getRoleById(Configuration.getInstance().getDotenv().get("DEFAULT_ROLE_ID"));

        if (role != null) {
            event.getGuild().addRoleToMember(member, role).queue();
        }
    }
}
