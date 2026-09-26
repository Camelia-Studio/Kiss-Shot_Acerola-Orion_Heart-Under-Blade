package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;

final class ApiJson {
    private ApiJson() {
    }

    static String nullableText(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.asText();
    }

    static Instant nullableInstant(JsonNode node) {
        String text = nullableText(node);
        return text == null ? null : OffsetDateTime.parse(text).toInstant();
    }
}
