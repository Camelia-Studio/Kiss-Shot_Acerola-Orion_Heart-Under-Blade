package org.camelia.studio.kiss.shot.acerola.models;

import java.time.Instant;

public record Averto(
        long id,
        String userId,
        String moderatorId,
        String reason,
        String file,
        Instant createdAt
) {
}
