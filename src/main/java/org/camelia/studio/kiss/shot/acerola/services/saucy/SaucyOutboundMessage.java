package org.camelia.studio.kiss.shot.acerola.services.saucy;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

public record SaucyOutboundMessage(
        String text,
        List<MessageEmbed> embeds,
        List<SaucyFileAttachment> files
) {
    public SaucyOutboundMessage {
        embeds = List.copyOf(embeds);
        files = List.copyOf(files);
    }

    public boolean isEmpty() {
        return (text == null || text.isBlank()) && embeds.isEmpty() && files.isEmpty();
    }
}
