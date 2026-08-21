package org.camelia.studio.kiss.shot.acerola.listeners.bot;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.services.DiscordServerService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Connecté en tant que {}", event.getJDA().getSelfUser().getAsTag());
        DiscordServerService.getInstance().synchronize(
                event.getJDA().getGuilds(),
                event.getJDA().getUnavailableGuilds());
    }
}
