
package org.camelia.studio.kiss.shot.acerola.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.*;
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

public class MsgSendCommand implements ISlashCommand {
    private final Logger logger = LoggerFactory.getLogger(MsgSendCommand.class);
    @Override
    public String getName() {
        return "msgsend";
    }

    @Override
    public String getDescription() {
        return "Permet d'envoyer un message en tant que le bot";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.CHANNEL, "channel", "Le salon où envoyer le message", true).setChannelTypes(ChannelType.NEWS, ChannelType.TEXT, ChannelType.GUILD_NEWS_THREAD, ChannelType.GUILD_PRIVATE_THREAD, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.VOICE, ChannelType.STAGE),
                new OptionData(OptionType.STRING, "message", "Le message à envoyer", false),
                new OptionData(OptionType.ATTACHMENT, "attachment", "L'embed à envoyer", false)

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

        GuildChannelUnion chan = Objects.requireNonNull(event.getOption("channel")).getAsChannel();
        OptionMapping message = event.getOption("message");
        OptionMapping attachment = event.getOption("attachment");

        if (message == null && attachment == null) {
            event.reply("Vous devez spécifier un message ou un embed à envoyer !").queue();
            return;
        }

        try {
            if (message != null) {
                if (chan.getType() == ChannelType.TEXT) {
                    TextChannel channel = chan.asTextChannel();
                    channel.sendMessage(message.getAsString()).queue();
                } else if (chan.getType() == ChannelType.GUILD_NEWS_THREAD || chan.getType() == ChannelType.GUILD_PRIVATE_THREAD || chan.getType() == ChannelType.GUILD_PUBLIC_THREAD) {
                    ThreadChannel channel = chan.asThreadChannel();
                    channel.sendMessage(message.getAsString()).queue();
                } else if (chan.getType() == ChannelType.NEWS) {
                    NewsChannel channel = chan.asNewsChannel();

                    channel.sendMessage(message.getAsString()).queue();
                }else if (chan.getType() == ChannelType.VOICE) {
                    VoiceChannel channel = chan.asVoiceChannel();
                    channel.sendMessage(message.getAsString()).queue();
                }else if (chan.getType() == ChannelType.STAGE) {
                    StageChannel channel = chan.asStageChannel();
                    channel.sendMessage(message.getAsString()).queue();
                }
            }

            if (attachment != null) {
                Message.Attachment file = attachment.getAsAttachment();
                String content = URLFileReader.readFileFromURL(file.getUrl());


                if (chan.getType() == ChannelType.TEXT) {
                    TextChannel channel = chan.asTextChannel();
                    channel.sendMessageEmbeds(EmbedBuilder.fromData(DataObject.fromJson(content)).build()).queue();
                } else if (chan.getType() == ChannelType.GUILD_NEWS_THREAD || chan.getType() == ChannelType.GUILD_PRIVATE_THREAD || chan.getType() == ChannelType.GUILD_PUBLIC_THREAD) {
                    ThreadChannel channel = chan.asThreadChannel();
                    channel.sendMessageEmbeds(EmbedBuilder.fromData(DataObject.fromJson(content)).build()).queue();
                } else if (chan.getType() == ChannelType.NEWS) {
                    NewsChannel channel = chan.asNewsChannel();
                    channel.sendMessageEmbeds(EmbedBuilder.fromData(DataObject.fromJson(content)).build()).queue();
                }else if (chan.getType() == ChannelType.VOICE) {
                    VoiceChannel channel = chan.asVoiceChannel();
                    channel.sendMessageEmbeds(EmbedBuilder.fromData(DataObject.fromJson(content)).build()).queue();
                }else if (chan.getType() == ChannelType.STAGE) {
                    StageChannel channel = chan.asStageChannel();
                    channel.sendMessageEmbeds(EmbedBuilder.fromData(DataObject.fromJson(content)).build()).queue();
                }
            }

            event.reply("Message envoyé !").queue();
        } catch (Exception e) {
            event.reply("Erreur lors de l'envoi du message !\n%s".formatted(e.getMessage())).queue();
            logger.error("Erreur lors de l'envoi du message", e);
        }
    }
}
