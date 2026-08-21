package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.services.DiscordServerService;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.jetbrains.annotations.NotNull;

public class GuildLifecycleListener extends ListenerAdapter {
    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        DiscordServerService.getInstance().register(event.getGuild().getId());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        DiscordServerService.getInstance().markLeft(event.getGuild().getId());
        ModuleConfigurationService.getInstance().evictGuild(event.getGuild().getId());
    }
}
