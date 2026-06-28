package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record MisskeyNote(
        @JsonProperty("id") String id,
        @JsonProperty("createdAt") @JsonAlias("created_at") String createdAt,
        @JsonProperty("text") String text,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("files") List<MisskeyFile> files,
        @JsonProperty("user") MisskeyUser user
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record MisskeyUser(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("username") String username,
        @JsonProperty("avatarUrl") @JsonAlias("avatar_url") String avatarUrl
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record MisskeyFile(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("size") int size,
        @JsonProperty("isSensitive") @JsonAlias("is_sensitive") boolean isSensitive,
        @JsonProperty("url") String url,
        @JsonProperty("thumbnailUrl") @JsonAlias("thumbnail_url") String thumbnailUrl
) {
}
