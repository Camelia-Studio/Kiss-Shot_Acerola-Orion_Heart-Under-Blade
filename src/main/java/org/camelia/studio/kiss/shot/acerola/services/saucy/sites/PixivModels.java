package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record PixivIllustrationResponse(
        @JsonProperty("error") boolean error,
        @JsonProperty("message") String message,
        @JsonProperty("body") PixivIllustration body
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record PixivIllustration(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("illustType") int illustType,
        @JsonProperty("pageCount") int pageCount,
        @JsonProperty("xRestrict") int xRestrict,
        @JsonProperty("urls") PixivIllustrationUrls urls
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record PixivIllustrationUrls(
        @JsonProperty("mini") String mini,
        @JsonProperty("thumb") String thumb,
        @JsonProperty("small") String small,
        @JsonProperty("regular") String regular,
        @JsonProperty("original") String original
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record PixivPagesResponse(
        @JsonProperty("error") boolean error,
        @JsonProperty("message") String message,
        @JsonProperty("body") List<PixivPage> body
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record PixivPage(
        @JsonProperty("urls") PixivPageUrls urls,
        @JsonProperty("width") int width,
        @JsonProperty("height") int height
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record PixivPageUrls(
        @JsonProperty("thumb_mini") String thumbMini,
        @JsonProperty("small") String small,
        @JsonProperty("regular") String regular,
        @JsonProperty("original") String original
) {
}
