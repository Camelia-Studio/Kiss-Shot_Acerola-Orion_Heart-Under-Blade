package org.camelia.studio.kiss.shot.acerola.managers;


import org.camelia.studio.kiss.shot.acerola.KissShotAcerola;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.utils.ReflectionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CommandManager {
    private final List<ISlashCommand> slashCommands;
    private final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    public CommandManager() {
        slashCommands = ReflectionUtils.loadClasses(
                "org.camelia.studio.kiss.shot.acerola.commands",
                ISlashCommand.class
        );
    }

    public void registerCommands() {
        var jda = KissShotAcerola.getJda();

        jda
                .updateCommands()
                .addCommands(
                        slashCommands
                                .stream()
                                .map((cmd) -> Commands
                                        .slash(cmd.getName(), cmd.getDescription())
                                        .addOptions(cmd.getOptions())
                                        .setDefaultPermissions(cmd.defaultPermissions())
                                        .setContexts(InteractionContextType.GUILD))
                                .toList()
                )
                .queue(
                        commands -> {
                            logger.info("{} commandes globales enregistrées", commands.size());
                            jda.getGuilds().forEach(guild -> guild.updateCommands().queue(
                                    guildCommands -> logger.debug(
                                            "Commandes de guilde supprimées pour {}",
                                            guild.getId()),
                                    error -> logger.error(
                                            "Impossible de supprimer les commandes de guilde pour {}",
                                            guild.getId(),
                                            error)));
                        },
                        error -> logger.error("Impossible d'enregistrer les commandes globales", error));
    }

    public void handleCommand(String commandName, SlashCommandInteractionEvent event) {

        for (ISlashCommand command : slashCommands) {
            if (command.getName().equals(commandName)) {
                command.execute(event);
                return;
            }
        }
        event.reply("Commande inconnue !").setEphemeral(true).queue();
    }
}
