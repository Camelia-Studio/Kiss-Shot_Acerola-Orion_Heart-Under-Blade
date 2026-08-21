package org.camelia.studio.kiss.shot.acerola.managers;


import org.camelia.studio.kiss.shot.acerola.KissShotAcerola;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleAccessResult;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
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
                                        .addSubcommands(cmd.getSubcommands())
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
                if (!command.requiredPermissions().isEmpty()
                        && (event.getMember() == null
                        || !event.getMember().hasPermission(command.requiredPermissions()))) {
                    event.reply("Vous ne possédez pas les permissions nécessaires pour cette commande.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                if (command.requiredModule().isPresent()) {
                    if (event.getGuild() == null) {
                        event.reply("Cette commande ne peut être utilisée que sur un serveur.")
                                .setEphemeral(true)
                                .queue();
                        return;
                    }
                    ModuleAccessResult access = ModuleConfigurationService.getInstance()
                            .checkAccess(event.getGuild(), command.requiredModule().get());
                    if (!access.allowed()) {
                        event.reply(access.message()).setEphemeral(true).queue();
                        return;
                    }
                }
                command.execute(event);
                return;
            }
        }
        event.reply("Commande inconnue !").setEphemeral(true).queue();
    }
}
