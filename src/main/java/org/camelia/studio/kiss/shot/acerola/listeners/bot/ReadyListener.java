package org.camelia.studio.kiss.shot.acerola.listeners.bot;

import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);

    @Override
    public void onReady(ReadyEvent event) {
        logger.info("Connecté en tant que {}", event.getJDA().getSelfUser().getAsTag());
        initRole(event.getJDA());
    }

    private void initRole(JDA jda) {
            Guild guild = jda.getGuildById(Configuration.getInstance().getDotenv().get("GUILD_ID"));
            if (guild != null) {
                Role role = guild.getRoleById(Configuration.getInstance().getDotenv().get("DEFAULT_ROLE_ID"));

                if (role == null) {
                    logger.error("Impossible de trouver le rôle par défaut");
                    return;
                }
                guild.loadMembers().onSuccess(members -> {
                    for (Member member : members) {
                        if (member.getRoles().contains(role)) {
                            continue;
                        }

                        guild.addRoleToMember(member, role).queue();

                        logger.info("Rôle ajouté à {}", member.getUser().getAsTag());
                    }
                });
            }
    }
}
