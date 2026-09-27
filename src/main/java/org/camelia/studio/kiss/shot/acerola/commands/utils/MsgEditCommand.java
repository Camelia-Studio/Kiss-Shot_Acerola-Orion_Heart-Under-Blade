package org.camelia.studio.kiss.shot.acerola.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
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

public class MsgEditCommand implements ISlashCommand {
    private static final Logger logger = LoggerFactory.getLogger(MsgEditCommand.class);

    @Override
    public String getName() {
        return "msgedit";
    }

    @Override
    public String getDescription() {
        return "Permet de modifier un message envoyé par le bot";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, "message_id", "L'id du message à modifier", true),
                new OptionData(OptionType.CHANNEL, "channel", "Le salon qui contient le message", true)
                        .setChannelTypes(
                                ChannelType.NEWS,
                                ChannelType.TEXT,
                                ChannelType.GUILD_NEWS_THREAD,
                                ChannelType.GUILD_PRIVATE_THREAD,
                                ChannelType.GUILD_PUBLIC_THREAD,
                                ChannelType.VOICE,
                                ChannelType.STAGE),
                new OptionData(OptionType.STRING, "message", "Le nouveau contenu", false),
                new OptionData(OptionType.ATTACHMENT, "attachment", "Le nouvel embed", false));
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

        String messageId = Objects.requireNonNull(event.getOption("message_id")).getAsString();
        GuildChannelUnion selectedChannel = Objects.requireNonNull(event.getOption("channel")).getAsChannel();
        OptionMapping message = event.getOption("message");
        OptionMapping attachment = event.getOption("attachment");
        if (message == null && attachment == null) {
            event.reply("Vous devez spécifier un message ou un embed à modifier !")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (selectedChannel.getGuild().getIdLong() != event.getGuild().getIdLong()) {
            event.reply("Le salon doit appartenir au serveur courant.").setEphemeral(true).queue();
            return;
        }

        GuildMessageChannel channel = selectedChannel.asGuildMessageChannel();
        if (!MsgSendCommand.hasChannelManagePermission(event.getMember(), channel)) {
            event.reply("Vous ne possédez pas les permissions nécessaires dans ce salon.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        if (!MsgSendCommand.hasBotPermissions(
                event.getGuild().getSelfMember(),
                channel,
                attachment != null,
                true)) {
            event.reply("Je ne possède pas les permissions nécessaires dans ce salon.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        try {
            MessageEmbed embed = attachment == null ? null : embedFrom(attachment);
            channel.retrieveMessageById(messageId).queue(
                    found -> editMessage(event, found, message, embed),
                    error -> event.getHook().editOriginal("Impossible de trouver le message spécifié.").queue());
        } catch (Exception exception) {
            logger.error("Erreur lors de la modification du message", exception);
            event.getHook().editOriginal("Erreur lors de la modification du message : " + exception.getMessage())
                    .queue();
        }
    }

    private void editMessage(
            SlashCommandInteractionEvent event,
            Message found,
            OptionMapping message,
            MessageEmbed embed
    ) {
        if (!found.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
            event.getHook().editOriginal("Le message spécifié n'a pas été envoyé par ce bot.").queue();
            return;
        }

        var action = found.editMessage(message == null ? found.getContentRaw() : message.getAsString());
        action.setEmbeds(embed == null ? found.getEmbeds() : List.of(embed));
        action.setAllowedMentions(
                MsgSendCommand.allowedMentionsFor(event.getMember(), found.getChannel().asGuildMessageChannel()));
        action.queue(
                success -> event.getHook().editOriginal("Message modifié !").queue(),
                error -> {
                    logger.error("Erreur lors de la modification du message", error);
                    event.getHook().editOriginal("Erreur lors de la modification du message : " + error.getMessage())
                            .queue();
                });
    }

    private MessageEmbed embedFrom(OptionMapping attachment) throws Exception {
        Message.Attachment file = attachment.getAsAttachment();
        String content = URLFileReader.readFileFromURL(file.getUrl());
        return EmbedBuilder.fromData(DataObject.fromJson(content)).build();
    }
}
