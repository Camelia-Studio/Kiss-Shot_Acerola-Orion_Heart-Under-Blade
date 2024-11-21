package org.camelia.studio.kiss.shot.acerola.interfaces;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public interface ISlashCommand {
    String getName();
    String getDescription();
    void execute(SlashCommandInteractionEvent event);
    default List<OptionData> getOptions() {
        return List.of();
    }
}

