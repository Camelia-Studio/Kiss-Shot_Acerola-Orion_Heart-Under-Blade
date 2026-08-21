package org.camelia.studio.kiss.shot.acerola.commands.moderation;

import java.util.List;
import java.util.Optional;

import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.models.Averto;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.AvertoService;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AvertoListCommand implements ISlashCommand {

    @Override
    public String getName() {
        return "avertolist";
    }

    @Override
    public String getDescription() {
        return "Liste les avertissements d'un utilisateur";
    }

    @Override
    public DefaultMemberPermissions defaultPermissions() {
        return DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(
                        OptionType.USER,
                        "utilisateur",
                        "L'utilisateur dont vous voulez voir les avertissements",
                        false));
    }

    @Override
    public Optional<ModuleType> requiredModule() {
        return Optional.of(ModuleType.WARNINGS);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();
        OptionMapping option = event.getOption("utilisateur");

        Member member = null;
        List<Averto> avertos = null;
        String guildId = event.getGuild().getId();

        if (option != null) {
            member = option.getAsMember();
            avertos = AvertoService.getInstance().getLatestAvertosForUser(guildId, member.getId(), 10);
        } else {
            avertos = AvertoService.getInstance().getLatestAvertos(guildId, 10);
        }
        /*
         * 2 possibilités :
         * - Aucun utilisateur : On affiche les 10 derniers avertissements du serveur
         * - Un utilisateur : On affiche les avertissements de cet utilisateur
         */

        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Avertissements de " + (member == null ? "tous les utilisateurs" : member.getEffectiveName()))
                .setColor(0xFF0000);

        int count = 0;

        for (Averto averto : avertos) {
            count++;
            if (count > 10) {
                break;
            }
            // On récupère le membre Discord de l'utilisateur
            Member discordUser = event.getGuild().getMemberById(averto.getUser().getDiscordId());
            Member moderator = event.getGuild().getMemberById(averto.getModerator().getDiscordId());
            embedBuilder.addField(
                    "Avertissement #" + averto.getId(),
                    (discordUser != null ? "Utilisateur : " + discordUser.getAsMention() + "\n" : "") +
                            "Raison : " + averto.getReason() + "\n" +
                            (moderator != null ? "Modérateur : " + moderator.getAsMention() : "") + "\n" +
                            "Date : "
                            + "<t:" + averto.getCreatedAt().getEpochSecond() + ":f>" +
                            "\n" +
                            "Preuve : " + (averto.getFile() != null ? averto.getFile() : "Aucune"),
                    false);
        }

        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
    }

}
