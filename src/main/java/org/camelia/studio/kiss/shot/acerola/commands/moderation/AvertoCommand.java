package org.camelia.studio.kiss.shot.acerola.commands.moderation;

import java.io.File;
import java.util.List;

import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.models.Averto;
import org.camelia.studio.kiss.shot.acerola.models.User;
import org.camelia.studio.kiss.shot.acerola.repositories.AvertoRepository;
import org.camelia.studio.kiss.shot.acerola.services.UserService;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

public class AvertoCommand implements ISlashCommand {

    @Override
    public String getName() {
        return "averto";
    }

    @Override
    public String getDescription() {
        return "Permet d'avertir un utilisateur";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.USER, "utilisateur", "L'utilisateur à avertir", true),
                new OptionData(OptionType.STRING, "raison", "La raison de l'avertissement", false),
                new OptionData(OptionType.ATTACHMENT, "file", "Une preuve de l'avertissement", false));
    }

    @Override
    public DefaultMemberPermissions defaultPermissions() {
        return DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();
        try {
            Member moderator = event.getMember();
            Member member = event.getOption("utilisateur").getAsMember();

            OptionMapping raisonOptionMapping = event.getOption("raison");
            String reason = raisonOptionMapping == null ? "Aucune raison spécifiée" : raisonOptionMapping.getAsString();

            OptionMapping fileOptionMapping = event.getOption("file");
            Attachment file = null;
            String fileUrl = null;
            TextChannel logChannel = event.getGuild()
                    .getTextChannelById(Configuration.getInstance().getDotenv().get("LOG_CHANNEL_ID"));

            if (fileOptionMapping != null) {
                file = fileOptionMapping.getAsAttachment();
            }

            if (logChannel != null) {
                File fileTemp = null;
                if (file != null) {
                    fileTemp = File.createTempFile("proof_" + member.getId() + "_", "." + file.getFileExtension());
                    fileTemp = file.getProxy().downloadToFile(fileTemp).get();
                }

                Message message = this.sendLogMessage(logChannel, member, fileTemp, reason);

                if (fileTemp != null) {
                    fileUrl = message.getAttachments().get(0).getUrl();
                    fileTemp.delete();
                }
            }

            User memberUser = UserService.getInstance().getOrCreateUser(member.getId());
            User moderatorUser = UserService.getInstance().getOrCreateUser(moderator.getId());

            Averto averto = new Averto(memberUser, moderatorUser);
            averto.setReason(reason);
            averto.setFile(fileUrl);

            AvertoRepository.getInstance().save(averto);

            // On tente d'envoyer un message privé à l'utilisateur averti
            member.getUser().openPrivateChannel().queue(privateChannel -> {
                privateChannel
                        .sendMessage("Bonjour, Vous avez été averti sur %s pour la raison suivante : %s".formatted(
                                event.getGuild().getName(), reason != null ? reason : "Aucune raison spécifiée"))
                        .queue();
            });

            event.getHook().editOriginal("L'utilisateur %s a bien été averti !".formatted(member.getAsMention()))
                    .queue();
        } catch (Exception e) {
            event.getHook().editOriginal("Une erreur est survenue lors de l'avertissement, " + e.getMessage()).queue();
        }
    }

    private Message sendLogMessage(TextChannel logChannel, Member member, File fileTemp, String reason) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Avertissement - Règlement enfreint");
        embedBuilder.setDescription("Un utilisateur a été averti pour non respect du règlement");
        embedBuilder.addField("Utilisateur", member.getAsMention(), false);
        embedBuilder.addField("Raison", reason != null ? reason : "Aucune raison spécifié", false);

        Message msg = logChannel.sendMessageEmbeds(embedBuilder.build()).complete();

        if (fileTemp != null) {
            msg = logChannel
                    .sendFiles(FileUpload.fromData(fileTemp)).complete();
        }

        return msg;
    }
}
