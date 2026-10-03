package org.camelia.studio.kiss.shot.acerola.models;

import java.time.Instant;

public record DiscordServer(
        String discordId,
        DiscordServerLifecycle lifecycle,
        Instant joinedAt,
        Instant leftAt,
        String locale,
        String logChannelId
) {
}
