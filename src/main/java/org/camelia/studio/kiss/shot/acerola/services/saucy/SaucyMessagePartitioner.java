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

        List<List<MessageEmbed>> embedChunks = embedChunks(response.embeds());
        List<List<SaucyFileAttachment>> fileChunks = fileChunks(response.files());

        if (!embedChunks.isEmpty() && !fileChunks.isEmpty()) {
            addIfNotEmpty(messages, new SaucyOutboundMessage(null, embedChunks.getFirst(), fileChunks.getFirst()));

            for (int index = 1; index < embedChunks.size(); index++) {
                addIfNotEmpty(messages, new SaucyOutboundMessage(null, embedChunks.get(index), List.of()));
            }
            for (int index = 1; index < fileChunks.size(); index++) {
                addIfNotEmpty(messages, new SaucyOutboundMessage(null, List.of(), fileChunks.get(index)));
            }

            return List.copyOf(messages);
        }

        for (List<MessageEmbed> embeds : embedChunks) {
            addIfNotEmpty(messages, new SaucyOutboundMessage(null, embeds, List.of()));
        }

        for (List<SaucyFileAttachment> files : fileChunks) {
            addIfNotEmpty(messages, new SaucyOutboundMessage(null, List.of(), files));
        }

        return List.copyOf(messages);
    }

    private List<List<MessageEmbed>> embedChunks(List<MessageEmbed> embeds) {
        List<List<MessageEmbed>> chunks = new ArrayList<>();
        for (int index = 0; index < embeds.size(); index += maxEmbedsPerMessage) {
            int end = Math.min(index + maxEmbedsPerMessage, embeds.size());
            chunks.add(List.copyOf(embeds.subList(index, end)));
        }

        return chunks;
    }

    private List<List<SaucyFileAttachment>> fileChunks(List<SaucyFileAttachment> files) {
        List<List<SaucyFileAttachment>> chunks = new ArrayList<>();
        List<SaucyFileAttachment> currentFiles = new ArrayList<>();
        long currentBytes = 0;

        for (SaucyFileAttachment file : files) {
            if (file.size() > maxFileBytes) {
                flushFiles(chunks, currentFiles);
                chunks.add(List.of(file));
                currentBytes = 0;
                continue;
            }

            if (!currentFiles.isEmpty()
                    && (currentBytes + file.size() > maxFileBytes
                    || currentFiles.size() >= Message.MAX_FILE_AMOUNT)) {
                flushFiles(chunks, currentFiles);
                currentBytes = 0;
            }

            currentFiles.add(file);
            currentBytes += file.size();
        }

        flushFiles(chunks, currentFiles);

        return chunks;
    }

    private static void flushFiles(List<List<SaucyFileAttachment>> chunks, List<SaucyFileAttachment> files) {
        if (!files.isEmpty()) {
            chunks.add(List.copyOf(files));
        }
        files.clear();
    }

    private static void addIfNotEmpty(List<SaucyOutboundMessage> messages, SaucyOutboundMessage message) {
        if (!message.isEmpty()) {
            messages.add(message);
        }
    }
}
