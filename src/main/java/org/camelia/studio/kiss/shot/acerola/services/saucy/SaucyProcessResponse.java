package org.camelia.studio.kiss.shot.acerola.services.saucy;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

public record SaucyProcessResponse(
        String text,
        List<MessageEmbed> embeds,
        List<SaucyFileAttachment> files,
        boolean sensitive
) {
    public SaucyProcessResponse {
        embeds = List.copyOf(embeds);
        files = List.copyOf(files);
    }

    public boolean isEmpty() {
        return (text == null || text.isBlank()) && embeds.isEmpty() && files.isEmpty();
    }
}
