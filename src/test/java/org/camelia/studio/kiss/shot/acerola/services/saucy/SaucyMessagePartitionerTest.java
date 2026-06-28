package org.camelia.studio.kiss.shot.acerola.services.saucy;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaucyMessagePartitionerTest {

    @Test
    void textOnlyResponseBecomesOneOutboundMessage() {
        SaucyMessagePartitioner partitioner = new SaucyMessagePartitioner(4, 100);
        SaucyProcessResponse response = new SaucyProcessResponse("hello", List.of(), List.of(), false);

        List<SaucyOutboundMessage> messages = partitioner.partition(response);

        assertEquals(1, messages.size());
        assertEquals("hello", messages.getFirst().text());
        assertEquals(List.of(), messages.getFirst().embeds());
        assertEquals(List.of(), messages.getFirst().files());
    }

    @Test
    void embedsAreChunkedByMaximumEmbedsPerMessage() {
        SaucyMessagePartitioner partitioner = new SaucyMessagePartitioner(4, 100);
        SaucyProcessResponse response = new SaucyProcessResponse(
                null,
                List.of(embed("embed-1"), embed("embed-2"), embed("embed-3"), embed("embed-4"), embed("embed-5")),
                List.of(),
                false
        );

        List<SaucyOutboundMessage> messages = partitioner.partition(response);

        assertEquals(2, messages.size());
        assertEquals(4, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
    }

    @Test
    void filesAreGroupedUntilMaximumBytesWouldBeExceeded() {
        SaucyMessagePartitioner partitioner = new SaucyMessagePartitioner(4, 10);
        SaucyFileAttachment first = file("first", 4);
        SaucyFileAttachment second = file("second", 6);
        SaucyFileAttachment third = file("third", 1);
        SaucyFileAttachment fourth = file("fourth", 11);
        SaucyProcessResponse response = new SaucyProcessResponse(
                null,
                List.of(),
                List.of(first, second, third, fourth),
                false
        );

        List<SaucyOutboundMessage> messages = partitioner.partition(response);

        assertEquals(3, messages.size());
        assertEquals(List.of(first, second), messages.get(0).files());
        assertEquals(List.of(third), messages.get(1).files());
        assertEquals(List.of(fourth), messages.get(2).files());
    }

    @Test
    void filesAreChunkedByDiscordMaximumFileCount() {
        SaucyMessagePartitioner partitioner = new SaucyMessagePartitioner(4, 100);
        List<SaucyFileAttachment> files = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            files.add(file("file-" + index, 1));
        }
        SaucyProcessResponse response = new SaucyProcessResponse(null, List.of(), files, false);

        List<SaucyOutboundMessage> messages = partitioner.partition(response);

        assertEquals(2, messages.size());
        assertEquals(10, messages.get(0).files().size());
        assertEquals(1, messages.get(1).files().size());
    }

    @Test
    void textWithFilesSendsTextMessageBeforeFileMessages() {
        SaucyMessagePartitioner partitioner = new SaucyMessagePartitioner(4, 10);
        SaucyFileAttachment file = file("file", 4);
        SaucyProcessResponse response = new SaucyProcessResponse("caption", List.of(), List.of(file), false);

        List<SaucyOutboundMessage> messages = partitioner.partition(response);

        assertEquals(2, messages.size());
        assertEquals("caption", messages.get(0).text());
        assertEquals(List.of(), messages.get(0).files());
        assertEquals(null, messages.get(1).text());
        assertEquals(List.of(file), messages.get(1).files());
    }

    @Test
    void rejectsZeroMaximumEmbedsPerMessage() {
        assertThrows(IllegalArgumentException.class, () -> new SaucyMessagePartitioner(0, 10));
    }

    @Test
    void rejectsZeroMaximumFileBytes() {
        assertThrows(IllegalArgumentException.class, () -> new SaucyMessagePartitioner(1, 0));
    }

    private static MessageEmbed embed(String description) {
        return new EmbedBuilder().setDescription(description).build();
    }

    private static SaucyFileAttachment file(String name, int size) {
        return new SaucyFileAttachment(name, new byte[size], "application/octet-stream");
    }
}
