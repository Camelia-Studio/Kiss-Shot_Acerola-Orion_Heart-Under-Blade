package org.camelia.studio.kiss.shot.acerola.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.camelia.studio.kiss.shot.acerola.interfaces.ISlashCommand;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.utils.URLFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class MsgSendCommand implements ISlashCommand {
    private static final Logger logger = LoggerFactory.getLogger(MsgSendCommand.class);

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
                new OptionData(OptionType.CHANNEL, "channel", "Le salon où envoyer le message", true)
                        .setChannelTypes(
                                ChannelType.NEWS,
                                ChannelType.TEXT,
                                ChannelType.GUILD_NEWS_THREAD,
                                ChannelType.GUILD_PRIVATE_THREAD,
                                ChannelType.GUILD_PUBLIC_THREAD,
                                ChannelType.VOICE,
                                ChannelType.STAGE),
                new OptionData(OptionType.STRING, "message", "Le message à envoyer", false),
                new OptionData(OptionType.ATTACHMENT, "attachment", "L'embed à envoyer", false));
    }

    @Override
    public Optional<ModuleType> requiredModule() {
        return Optional.of(ModuleType.BOT_MESSAGES);
    }

    @Override
    public DefaultMemberPermissions defaultPermissions() {
        return DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE);
    }

    @Override
    public Set<Permission> requiredPermissions() {
        return Set.of(Permission.MESSAGE_MANAGE);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("Cette commande ne peut être utilisée que sur un serveur !")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        GuildChannelUnion selectedChannel = Objects.requireNonNull(event.getOption("channel")).getAsChannel();
        OptionMapping message = event.getOption("message");
        OptionMapping attachment = event.getOption("attachment");
        if (message == null && attachment == null) {
            event.reply("Vous devez spécifier un message ou un embed à envoyer !")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (selectedChannel.getGuild().getIdLong() != event.getGuild().getIdLong()) {
            event.reply("Le salon doit appartenir au serveur courant.").setEphemeral(true).queue();
            return;
        }

        GuildMessageChannel channel = selectedChannel.asGuildMessageChannel();
        if (!hasBotPermissions(event.getGuild().getSelfMember(), channel, attachment != null, false)) {
            event.reply("Je ne possède pas les permissions nécessaires dans ce salon.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        try {
            MessageEmbed embed = attachment == null ? null : embedFrom(attachment);
            var action = message == null
                    ? channel.sendMessageEmbeds(embed)
                    : channel.sendMessage(message.getAsString());
            if (message != null && embed != null) {
                action.setEmbeds(embed);
            }
            action.queue(
                    success -> event.getHook().editOriginal("Message envoyé !").queue(),
                    error -> {
                        logger.error("Erreur lors de l'envoi du message", error);
                        event.getHook().editOriginal("Erreur lors de l'envoi du message : " + error.getMessage())
                                .queue();
                    });
        } catch (Exception exception) {
            logger.error("Erreur lors de l'envoi du message", exception);
            event.getHook().editOriginal("Erreur lors de l'envoi du message : " + exception.getMessage()).queue();
        }
    }

    private MessageEmbed embedFrom(OptionMapping attachment) throws Exception {
        Message.Attachment file = attachment.getAsAttachment();
        String content = URLFileReader.readFileFromURL(file.getUrl());
        return EmbedBuilder.fromData(DataObject.fromJson(content)).build();
    }

    static boolean hasBotPermissions(
            Member self,
            GuildMessageChannel channel,
            boolean embeds,
            boolean history
    ) {
        Permission sendPermission = channel.getType().isThread()
                ? Permission.MESSAGE_SEND_IN_THREADS
                : Permission.MESSAGE_SEND;
        if (!self.hasPermission(channel, Permission.VIEW_CHANNEL, sendPermission)) {
            return false;
        }
        if (embeds && !self.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
            return false;
        }
        return !history || self.hasPermission(channel, Permission.MESSAGE_HISTORY);
    }
}
