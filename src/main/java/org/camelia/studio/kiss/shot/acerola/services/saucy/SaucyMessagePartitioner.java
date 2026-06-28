package org.camelia.studio.kiss.shot.acerola.services.saucy;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.List;

public class SaucyMessagePartitioner {
    private final int maxEmbedsPerMessage;
    private final long maxFileBytes;

    public SaucyMessagePartitioner(int maxEmbedsPerMessage, long maxFileBytes) {
        if (maxEmbedsPerMessage <= 0) {
            throw new IllegalArgumentException("maxEmbedsPerMessage must be positive");
        }
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }

        this.maxEmbedsPerMessage = maxEmbedsPerMessage;
        this.maxFileBytes = maxFileBytes;
    }

    public List<SaucyOutboundMessage> partition(SaucyProcessResponse response) {
        List<SaucyOutboundMessage> messages = new ArrayList<>();

        if (response.text() != null && !response.text().isBlank()) {
            addIfNotEmpty(messages, new SaucyOutboundMessage(response.text(), List.of(), List.of()));
        }

        for (int index = 0; index < response.embeds().size(); index += maxEmbedsPerMessage) {
            int end = Math.min(index + maxEmbedsPerMessage, response.embeds().size());
            List<MessageEmbed> embeds = response.embeds().subList(index, end);
            addIfNotEmpty(messages, new SaucyOutboundMessage(null, embeds, List.of()));
        }

        List<SaucyFileAttachment> currentFiles = new ArrayList<>();
        long currentBytes = 0;

        for (SaucyFileAttachment file : response.files()) {
            if (file.size() > maxFileBytes) {
                flushFiles(messages, currentFiles);
                addIfNotEmpty(messages, new SaucyOutboundMessage(null, List.of(), List.of(file)));
                currentBytes = 0;
                continue;
            }

            if (!currentFiles.isEmpty()
                    && (currentBytes + file.size() > maxFileBytes
                    || currentFiles.size() >= Message.MAX_FILE_AMOUNT)) {
                flushFiles(messages, currentFiles);
                currentBytes = 0;
            }

            currentFiles.add(file);
            currentBytes += file.size();
        }

        flushFiles(messages, currentFiles);

        return List.copyOf(messages);
    }

    private static void flushFiles(List<SaucyOutboundMessage> messages, List<SaucyFileAttachment> files) {
        addIfNotEmpty(messages, new SaucyOutboundMessage(null, List.of(), files));
        files.clear();
    }

    private static void addIfNotEmpty(List<SaucyOutboundMessage> messages, SaucyOutboundMessage message) {
        if (!message.isEmpty()) {
            messages.add(message);
        }
    }
}
