package org.camelia.studio.kiss.shot.acerola.interfaces;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ISlashCommand {
    String getName();

    String getDescription();

    void execute(SlashCommandInteractionEvent event);

    default List<OptionData> getOptions() {
        return List.of();
    }

    default List<SubcommandData> getSubcommands() {
        return List.of();
    }

    default Optional<ModuleType> requiredModule() {
        return Optional.empty();
    }

    default DefaultMemberPermissions defaultPermissions() {
        return DefaultMemberPermissions.ENABLED;
    }

    default Set<Permission> requiredPermissions() {
        return Set.of();
    }
}
