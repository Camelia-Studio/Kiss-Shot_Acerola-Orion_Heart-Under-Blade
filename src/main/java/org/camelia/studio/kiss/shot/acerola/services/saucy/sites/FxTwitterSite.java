package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyFileAttachment;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMatch;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyProcessResponse;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucySite;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FxTwitterSite implements SaucySite {
    private static final String ID = "fxtwitter";
    private static final int TWITTER_BLUE = 0x1DA1F2;
    private static final int MAX_DESCRIPTION_LENGTH = 4_096;
    private static final String DESCRIPTION_TRUNCATION_SUFFIX = "...";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?(?:mobile\\.twitter\\.com|twitter\\.com|x\\.com|nitter\\.com|nitter\\.net)/" +
                    "(?<user>[A-Za-z0-9_]+)/status/(?<id>\\d+)(?:/(?:video|photo)/\\d+)?" +
                    "(?:/(?<translate>\\w{2}|\\w{2}_\\w{2}))?" +
                    "(?=$|[\\s<>)\\].,!?:;]|\\?)"
    );

    private final FxTwitterGateway gateway;
    private final SaucyLinkEmbedConfig config;

    FxTwitterSite(FxTwitterGateway gateway, SaucyLinkEmbedConfig config) {
        this.gateway = gateway;
        this.config = config;
    }

    public FxTwitterSite(SaucyLinkEmbedConfig config, SaucyLinkCache<String> cache) {
        this(new FxTwitterClient(HttpClient.newHttpClient(), new ObjectMapper(), cache), config);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<SaucyMatch> match(String content, int remainingSlots) {
        if (remainingSlots <= 0 || content == null || content.isBlank()) {
            return List.of();
        }

        List<SaucyMatch> matches = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find() && matches.size() < remainingSlots) {
            Map<String, String> groups = new LinkedHashMap<>();
            groups.put("user", matcher.group("user"));
            groups.put("id", matcher.group("id"));
            groups.put("translate", Optional.ofNullable(matcher.group("translate")).orElse(""));
            matches.add(new SaucyMatch(ID, matcher.group(), groups));
        }

        return matches;
    }

    @Override
    public CompletableFuture<Optional<SaucyProcessResponse>> process(SaucyMatch match) {
        return CompletableFuture.supplyAsync(() -> processSynchronously(match));
    }

    private Optional<SaucyProcessResponse> processSynchronously(SaucyMatch match) {
        String user = match.groups().get("user");
        String id = match.groups().get("id");
        String translate = normalize(match.groups().get("translate"));
        Optional<FxTwitterResponse> response = gateway.getTweet(user, id, translate);
        if (response.isEmpty() || response.get().tweet() == null) {
            return Optional.empty();
        }

        FxTwitterTweet tweet = response.get().tweet();
        FxTwitterVideo video = firstVideo(tweet).orElse(null);
        if (video != null && video.url() != null && !video.url().isBlank()) {
            long contentLength = gateway.contentLength(video.url());
            if (contentLength > config.maxFileBytes()) {
                return Optional.of(fallback(tweet, id, user));
            }

            byte[] bytes = gateway.download(video.url(), config.maxFileBytes());
            if (bytes.length == 0 || bytes.length > config.maxFileBytes()) {
                return Optional.of(fallback(tweet, id, user));
            }

            SaucyFileAttachment file = new SaucyFileAttachment(fileName(video.url(), id), bytes, contentType(video));
            return Optional.of(new SaucyProcessResponse(
                    null,
                    List.of(embed(tweet, null)),
                    List.of(file),
                    sensitive(tweet)
            ));
        }

        List<String> photoUrls = photoUrls(tweet);
        if (!photoUrls.isEmpty()) {
            List<MessageEmbed> embeds = photoUrls.stream()
                    .map(photoUrl -> embed(tweet, originalPhotoUrl(photoUrl)))
                    .toList();
            return Optional.of(new SaucyProcessResponse(null, embeds, List.of(), sensitive(tweet)));
        }

        return Optional.of(new SaucyProcessResponse(
                null,
                List.of(embed(tweet, null)),
                List.of(),
                sensitive(tweet)
        ));
    }

    private static SaucyProcessResponse fallback(FxTwitterTweet tweet, String id, String user) {
        return new SaucyProcessResponse(
                "https://fxtwitter.com/%s/status/%s".formatted(screenName(tweet, user), id),
                List.of(),
                List.of(),
                sensitive(tweet)
        );
    }

    private static MessageEmbed embed(FxTwitterTweet tweet, String imageUrl) {
        EmbedBuilder builder = new EmbedBuilder()
                .setColor(TWITTER_BLUE)
                .setDescription(description(tweet))
                .setFooter("Twitter")
                .addField("Replies", String.valueOf(number(tweet.replies())), true)
                .addField("Retweets", String.valueOf(number(tweet.retweets())), true)
                .addField("Likes", String.valueOf(number(tweet.likes())), true)
                .addField("Views", String.valueOf(number(tweet.views())), true);

        FxTwitterAuthor author = tweet.author();
        if (author != null) {
            String screenName = screenName(tweet, "");
            String name = author.name() == null || author.name().isBlank() ? screenName : author.name();
            builder.setAuthor("%s (@%s)".formatted(name, screenName), authorUrl(author), author.avatarUrl());
        }

        if (tweet.createdTimestamp() != null) {
            builder.setTimestamp(Instant.ofEpochSecond(tweet.createdTimestamp()));
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            builder.setImage(imageUrl);
        }

        return builder.build();
    }

    private static String description(FxTwitterTweet tweet) {
        String description;
        if (tweet.translation() != null && tweet.translation().text() != null && !tweet.translation().text().isBlank()) {
            description = tweet.translation().text();
        } else {
            description = tweet.text() == null ? "" : tweet.text();
        }

        if (description.length() <= MAX_DESCRIPTION_LENGTH) {
            return description;
        }

        return description.substring(0, MAX_DESCRIPTION_LENGTH - DESCRIPTION_TRUNCATION_SUFFIX.length())
                + DESCRIPTION_TRUNCATION_SUFFIX;
    }

    private static List<String> photoUrls(FxTwitterTweet tweet) {
        if (tweet.media() == null || tweet.media().photos() == null) {
            return List.of();
        }

        return tweet.media().photos().stream()
                .map(FxTwitterPhoto::url)
                .filter(FxTwitterSite::isHttpUrl)
                .toList();
    }

    private static Optional<FxTwitterVideo> firstVideo(FxTwitterTweet tweet) {
        if (tweet.media() == null || tweet.media().videos() == null) {
            return Optional.empty();
        }

        return tweet.media().videos().stream().findFirst();
    }

    private static String originalPhotoUrl(String url) {
        if (url.contains("name=")) {
            return url.replaceAll("([?&]name=)[^&]+", "$1orig");
        }

        return url + (url.contains("?") ? "&" : "?") + "name=orig";
    }

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static String fileName(String url, String id) {
        try {
            String path = new URI(url).getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                String name = slash >= 0 ? path.substring(slash + 1) : path;
                if (!name.isBlank()) {
                    return name;
                }
            }
        } catch (IllegalArgumentException | URISyntaxException ignored) {
        }

        return "tweet-" + id + ".mp4";
    }

    private static String contentType(FxTwitterVideo video) {
        if (video.format() != null && !video.format().isBlank()) {
            return "video/" + video.format();
        }

        return "video/mp4";
    }

    private static String authorUrl(FxTwitterAuthor author) {
        if (author.url() != null && !author.url().isBlank()) {
            return author.url();
        }

        return "https://twitter.com/" + author.screenName();
    }

    private static String screenName(FxTwitterTweet tweet, String fallback) {
        if (tweet.author() != null && tweet.author().screenName() != null && !tweet.author().screenName().isBlank()) {
            return tweet.author().screenName();
        }

        return fallback;
    }

    private static long number(Long value) {
        return value == null ? 0L : value;
    }

    private static boolean sensitive(FxTwitterTweet tweet) {
        return Boolean.TRUE.equals(tweet.possiblySensitive());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }
}
