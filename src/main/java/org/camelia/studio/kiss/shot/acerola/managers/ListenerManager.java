package org.camelia.studio.kiss.shot.acerola.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.camelia.studio.kiss.shot.acerola.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ListenerManager {
    private final List<ListenerAdapter> listener;
    private final Logger logger = LoggerFactory.getLogger(ListenerManager.class.getName());

    public ListenerManager() {
        listener = ReflectionUtils.loadClasses(
                "org.camelia.studio.kiss.shot.acerola.listeners.global",
                ListenerAdapter.class);
    }

    public void registerListeners(JDA jda) {
        for (ListenerAdapter listenerAdapter : listener) {
            jda.addEventListener(listenerAdapter);

            logger.info("Listener {} enregistré !", listenerAdapter.getClass().getSimpleName());
        }
    }
}
