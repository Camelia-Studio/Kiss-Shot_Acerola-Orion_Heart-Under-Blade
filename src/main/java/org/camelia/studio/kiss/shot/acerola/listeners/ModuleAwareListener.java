package org.camelia.studio.kiss.shot.acerola.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;

import java.util.Optional;

public abstract class ModuleAwareListener extends ListenerAdapter {
    private final ModuleType module;

    protected ModuleAwareListener(ModuleType module) {
        this.module = module;
    }

    protected Optional<ModuleConfiguration> activeConfiguration(Guild guild) {
        return ModuleConfigurationService.getInstance().activeConfiguration(guild, module);
    }
}
