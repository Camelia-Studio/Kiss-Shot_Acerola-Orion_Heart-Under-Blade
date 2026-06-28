package org.camelia.studio.kiss.shot.acerola.services.saucy;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.attribute.IAgeRestrictedChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SaucyMessageSender {
    private static final Logger logger = LoggerFactory.getLogger(SaucyMessageSender.class);

    private final SaucyLinkEmbedConfig config;
    private final SaucyMessagePartitioner partitioner;
    private final SaucyNsfwGuard nsfwGuard;

    public SaucyMessageSender(SaucyLinkEmbedConfig config, SaucyMessagePartitioner partitioner, SaucyNsfwGuard nsfwGuard) {
        this.config = config;
        this.partitioner = partitioner;
        this.nsfwGuard = nsfwGuard;
    }

    public void send(Message sourceMessage, List<SaucyProcessResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        boolean channelNsfw = isNsfw(sourceMessage);
        logSkippedSensitiveResponses(sourceMessage, responses, channelNsfw);
        List<SaucyOutboundMessage> messages = prepareOutboundMessages(responses, channelNsfw);
        if (messages.isEmpty()) {
            return;
        }

        AtomicReference<Message> temporaryMessage = new AtomicReference<>();
        AtomicBoolean finalDispatchComplete = new AtomicBoolean(false);
        AtomicBoolean finalMessageSucceeded = new AtomicBoolean(false);
        AtomicInteger pendingFinalMessages = new AtomicInteger(messages.size());

        sendTemporaryMessage(sourceMessage, temporaryMessage, finalDispatchComplete);

        for (SaucyOutboundMessage message : messages) {
            queueFinalMessage(
                    sourceMessage,
                    message,
                    pendingFinalMessages,
                    finalMessageSucceeded,
                    finalDispatchComplete,
                    temporaryMessage
            );
        }
    }

    List<SaucyOutboundMessage> prepareOutboundMessages(List<SaucyProcessResponse> responses, boolean channelNsfw) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        List<SaucyOutboundMessage> messages = new ArrayList<>();
        for (SaucyProcessResponse response : responses) {
            if (!nsfwGuard.canPost(response.sensitive(), channelNsfw)) {
                continue;
            }

            messages.addAll(partitioner.partition(response));
        }

        return List.copyOf(messages);
    }

    private void sendTemporaryMessage(
            Message sourceMessage,
            AtomicReference<Message> temporaryMessage,
            AtomicBoolean finalDispatchComplete
    ) {
        if (!config.sendMatchedMessage() || config.matchedMessage() == null || config.matchedMessage().isBlank()) {
            return;
        }

        try {
            sourceMessage.reply(config.matchedMessage())
                    .mentionRepliedUser(false)
                    .setAllowedMentions(List.of())
                    .queue(
                            message -> {
                                temporaryMessage.set(message);
                                if (finalDispatchComplete.get()) {
                                    deleteTemporaryMessageIfPresent(temporaryMessage, sourceMessage);
                                }
                            },
                            error -> logger.warn(
                                    "Failed to send saucy temporary message for message {} in channel {}",
                                    sourceMessage.getId(),
                                    sourceMessage.getChannelId(),
                                    error
                            )
                    );
        } catch (RuntimeException exception) {
            logger.warn(
                    "Failed to queue saucy temporary message for message {} in channel {}",
                    sourceMessage.getId(),
                    sourceMessage.getChannelId(),
                    exception
            );
        }
    }

    private void queueFinalMessage(
            Message sourceMessage,
            SaucyOutboundMessage message,
            AtomicInteger pendingFinalMessages,
            AtomicBoolean finalMessageSucceeded,
            AtomicBoolean finalDispatchComplete,
            AtomicReference<Message> temporaryMessage
    ) {
        try {
            sourceMessage.reply(buildMessage(message))
                    .mentionRepliedUser(false)
                    .setAllowedMentions(List.of())
                    .queue(
                            ignored -> completeFinalMessage(
                                    sourceMessage,
                                    pendingFinalMessages,
                                    finalMessageSucceeded,
                                    finalDispatchComplete,
                                    temporaryMessage,
                                    true
                            ),
                            error -> {
                                logger.warn(
                                        "Failed to send saucy message for message {} in channel {}",
                                        sourceMessage.getId(),
                                        sourceMessage.getChannelId(),
                                        error
                                );
                                completeFinalMessage(
                                        sourceMessage,
                                        pendingFinalMessages,
                                        finalMessageSucceeded,
                                        finalDispatchComplete,
                                        temporaryMessage,
                                        false
                                );
                            }
                    );
        } catch (RuntimeException exception) {
            logger.warn(
                    "Failed to queue saucy message for message {} in channel {}",
                    sourceMessage.getId(),
                    sourceMessage.getChannelId(),
                    exception
            );
            completeFinalMessage(
                    sourceMessage,
                    pendingFinalMessages,
                    finalMessageSucceeded,
                    finalDispatchComplete,
                    temporaryMessage,
                    false
            );
        }
    }

    private MessageCreateData buildMessage(SaucyOutboundMessage message) {
        MessageCreateBuilder builder = new MessageCreateBuilder();

        if (message.text() != null && !message.text().isBlank()) {
            builder.setContent(message.text());
        }
        if (!message.embeds().isEmpty()) {
            builder.addEmbeds(message.embeds());
        }
        if (!message.files().isEmpty()) {
            builder.addFiles(message.files().stream()
                    .map(file -> FileUpload.fromData(file.data(), file.fileName()))
                    .toList());
        }

        return builder.build();
    }

    private void completeFinalMessage(
            Message sourceMessage,
            AtomicInteger pendingFinalMessages,
            AtomicBoolean finalMessageSucceeded,
            AtomicBoolean finalDispatchComplete,
            AtomicReference<Message> temporaryMessage,
            boolean succeeded
    ) {
        if (succeeded) {
            finalMessageSucceeded.set(true);
        }

        if (pendingFinalMessages.decrementAndGet() != 0) {
            return;
        }

        finalDispatchComplete.set(true);
        deleteTemporaryMessageIfPresent(temporaryMessage, sourceMessage);

        if (finalMessageSucceeded.get()) {
            suppressSourceEmbeds(sourceMessage);
        }
    }

    private void deleteTemporaryMessageIfPresent(AtomicReference<Message> temporaryMessage, Message sourceMessage) {
        Message message = temporaryMessage.getAndSet(null);
        if (message != null) {
            deleteTemporaryMessage(message, sourceMessage);
        }
    }

    private void deleteTemporaryMessage(Message temporaryMessage, Message sourceMessage) {
        temporaryMessage.delete().queue(
                ignored -> {
                },
                error -> logger.warn(
                        "Failed to delete saucy temporary message for message {} in channel {}",
                        sourceMessage.getId(),
                        sourceMessage.getChannelId(),
                        error
                )
        );
    }

    private void suppressSourceEmbeds(Message sourceMessage) {
        if (!canSuppressEmbeds(sourceMessage)) {
            return;
        }

        sourceMessage.suppressEmbeds(true).queue(
                ignored -> logger.info(
                        "Suppressed native embeds after saucy message {} in channel {}",
                        sourceMessage.getId(),
                        sourceMessage.getChannelId()
                ),
                error -> logger.warn(
                        "Failed to suppress native embeds after saucy message {} in channel {}",
                        sourceMessage.getId(),
                        sourceMessage.getChannelId(),
                        error
                )
        );
    }

    private boolean canSuppressEmbeds(Message sourceMessage) {
        try {
            GuildChannel channel = sourceMessage.getGuildChannel();
            return channel.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE);
        } catch (RuntimeException exception) {
            logger.warn(
                    "Cannot check saucy embed suppression permission for message {} in channel {}",
                    sourceMessage.getId(),
                    sourceMessage.getChannelId(),
                    exception
            );
            return false;
        }
    }

    private boolean isNsfw(Message sourceMessage) {
        return sourceMessage.getGuildChannel() instanceof IAgeRestrictedChannel ageRestricted && ageRestricted.isNSFW();
    }

    private void logSkippedSensitiveResponses(
            Message sourceMessage,
            List<SaucyProcessResponse> responses,
            boolean channelNsfw
    ) {
        if (channelNsfw) {
            return;
        }

        for (SaucyProcessResponse response : responses) {
            if (response.sensitive()) {
                logger.info(
                        "Skipping sensitive saucy response for non-NSFW channel {} message {}",
                        sourceMessage.getChannelId(),
                        sourceMessage.getId()
                );
            }
        }
    }
}
