package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record FxTwitterResponse(
        @JsonProperty("tweet") FxTwitterTweet tweet
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FxTwitterTweet(
        @JsonProperty("id") String id,
        @JsonProperty("url") String url,
        @JsonProperty("text") String text,
        @JsonProperty("created_timestamp") Long createdTimestamp,
        @JsonProperty("author") FxTwitterAuthor author,
        @JsonProperty("replies") Long replies,
        @JsonProperty("retweets") Long retweets,
        @JsonProperty("likes") Long likes,
        @JsonProperty("views") Long views,
        @JsonProperty("possibly_sensitive") Boolean possiblySensitive,
        @JsonProperty("media") FxTwitterMedia media,
        @JsonProperty("translation") FxTwitterTranslation translation,
        @JsonProperty("quote") FxTwitterTweet quote
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FxTwitterAuthor(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("screen_name") String screenName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("url") String url
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FxTwitterMedia(
        @JsonProperty("photos") List<FxTwitterPhoto> photos,
        @JsonProperty("videos") List<FxTwitterVideo> videos
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FxTwitterPhoto(
        @JsonProperty("type") String type,
        @JsonProperty("url") String url,
        @JsonProperty("width") Integer width,
        @JsonProperty("height") Integer height
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FxTwitterVideo(
        @JsonProperty("type") String type,
        @JsonProperty("url") String url,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("width") Integer width,
        @JsonProperty("height") Integer height,
        @JsonProperty("format") String format
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record FxTwitterTranslation(
        @JsonProperty("text") String text,
        @JsonProperty("source_lang") String sourceLang,
        @JsonProperty("target_lang") String targetLang
) {
}
