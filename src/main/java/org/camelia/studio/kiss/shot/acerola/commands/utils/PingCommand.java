package org.camelia.studio.kiss.shot.acerola.commands.utils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;

public class PingCommand implements ISlashCommand {
    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Envoie pong !";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.reply("Pong !").queue();
    }
}
