package org.camelia.studio.kiss.shot.acerola.commands.moderation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvertoListCommand implements ISlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(AvertoListCommand.class);

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
    public Set<Permission> requiredPermissions() {
        return Set.of(Permission.MODERATE_MEMBERS);
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

        try {
            if (option != null) {
                member = option.getAsMember();
                avertos = AvertoService.getInstance().getLatestAvertosForUser(guildId, member.getId(), 10);
            } else {
                avertos = AvertoService.getInstance().getLatestAvertos(guildId, 10);
            }
        } catch (RuntimeException exception) {
            logger.error("Impossible de récupérer les avertissements du serveur {}", guildId, exception);
            event.getHook().editOriginal("Les avertissements sont temporairement indisponibles. Réessayez plus tard.")
                    .queue();
            return;
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
            Member discordUser = event.getGuild().getMemberById(averto.userId());
            Member moderator = event.getGuild().getMemberById(averto.moderatorId());
            embedBuilder.addField(
                    "Avertissement #" + averto.id(),
                    (discordUser != null ? "Utilisateur : " + discordUser.getAsMention() + "\n" : "") +
                            "Raison : " + averto.reason() + "\n" +
                            (moderator != null ? "Modérateur : " + moderator.getAsMention() : "") + "\n" +
                            "Date : "
                            + "<t:" + averto.createdAt().getEpochSecond() + ":f>" +
                            "\n" +
                            "Preuve : " + (averto.file() != null ? averto.file() : "Aucune"),
                    false);
        }

        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
    }

}
