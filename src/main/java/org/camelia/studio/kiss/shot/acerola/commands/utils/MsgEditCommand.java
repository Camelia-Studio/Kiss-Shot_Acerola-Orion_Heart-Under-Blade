
package org.camelia.studio.kiss.shot.acerola.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.camelia.studio.kiss.shot.acerola.utils.URLFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class MsgEditCommand implements ISlashCommand {
    private final Logger logger = LoggerFactory.getLogger(MsgEditCommand.class);
    @Override
    public String getName() {
        return "msgedit";
    }

    @Override
    public String getDescription() {
        return "Permet de modifier un message qu'à envoyer le bot";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, "message_id", "L'id du message à modifier", true),
                new OptionData(OptionType.CHANNEL, "channel", "Le channel qui contient le message", true).setChannelTypes(ChannelType.NEWS, ChannelType.TEXT, ChannelType.GUILD_NEWS_THREAD, ChannelType.GUILD_PRIVATE_THREAD, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.VOICE, ChannelType.STAGE),
                new OptionData(OptionType.STRING, "message", "Le nouveau contenu", false),
                new OptionData(OptionType.ATTACHMENT, "attachment", "Le nouvel embed", false)

        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        if (!event.isFromGuild()) {
            event.reply("Cette commande ne peut être utilisée que sur un serveur !").queue();
            return;
        }
        Role roleNeeded = Objects.requireNonNull(event.getGuild()).getRoleById(Configuration.getInstance().getDotenv().get("ROLE_ID"));
        if (roleNeeded == null) {
            event.reply("Impossible de trouver le rôle nécessaire pour utiliser cette commande !").queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().getRoles().contains(roleNeeded)) {
            event.reply("Vous n'avez pas la permission d'utiliser cette commande !").queue();
            return;
        }

        String messageId = Objects.requireNonNull(event.getOption("message_id")).getAsString();

        GuildChannelUnion chan = Objects.requireNonNull(event.getOption("channel")).getAsChannel();
        OptionMapping message = event.getOption("message");
        OptionMapping attachment = event.getOption("attachment");

        if (message == null && attachment == null) {
            event.reply("Vous devez spécifier un message ou un embed à envoyer !").queue();
            return;
        }

        GuildMessageChannel realChannel = null;

        try {
            if (chan.getType() == ChannelType.TEXT) {
                realChannel = chan.asTextChannel();
            } else if (chan.getType() == ChannelType.GUILD_NEWS_THREAD || chan.getType() == ChannelType.GUILD_PRIVATE_THREAD || chan.getType() == ChannelType.GUILD_PUBLIC_THREAD) {
                realChannel = chan.asThreadChannel();
            } else if (chan.getType() == ChannelType.NEWS) {
                realChannel = chan.asNewsChannel();

            }else if (chan.getType() == ChannelType.VOICE) {
                realChannel = chan.asVoiceChannel();
            }else if (chan.getType() == ChannelType.STAGE) {
                realChannel = chan.asStageChannel();
            }

            if (realChannel == null) {
                event.reply("Impossible de trouver le channel spécifié !").queue();
                return;
            }
            Message realMessage = realChannel.retrieveMessageById(messageId).complete();

            if (realMessage == null || !realMessage.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
                event.reply("Impossible de trouver le message spécifié !").queue();
                return;
            }

            if (message != null) {
                realMessage.editMessage(message.getAsString()).queue();
            }

            if (attachment != null) {
                Message.Attachment file = attachment.getAsAttachment();
                String content = URLFileReader.readFileFromURL(file.getUrl());
                realMessage.editMessageEmbeds(EmbedBuilder.fromData(DataObject.fromJson(content)).build()).queue();
            }

            event.reply("Message modifié !").queue();
        } catch (Exception e) {
            event.reply("Erreur lors de la modification du message !\n%s".formatted(e.getMessage())).queue();
            logger.error("Erreur lors de l'envoi du message", e);
        }
    }
}
