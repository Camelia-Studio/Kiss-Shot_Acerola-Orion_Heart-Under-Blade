package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyFileAttachment;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMatch;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyProcessResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxTwitterSiteTest {
    @Test
    void matchesTwitterAndXUrls() {
        FxTwitterSite site = new FxTwitterSite(new FakeFxTwitterGateway(), config(1024));
        String content = """
                https://twitter.com/alice/status/123
                https://x.com/bob/status/456/en
                https://mobile.twitter.com/chloe/status/789
                https://nitter.net/dana/status/111
                https://nitter.com/erin/status/222
                """;

        List<SaucyMatch> limitedMatches = site.match(content, 3);
        List<SaucyMatch> allMatches = site.match(content, 10);

        assertEquals(3, limitedMatches.size());
        assertEquals(5, allMatches.size());
        assertEquals("alice", allMatches.get(0).groups().get("user"));
        assertEquals("123", allMatches.get(0).groups().get("id"));
        assertEquals("bob", allMatches.get(1).groups().get("user"));
        assertEquals("456", allMatches.get(1).groups().get("id"));
        assertEquals("en", allMatches.get(1).groups().get("translate"));
        assertEquals("chloe", allMatches.get(2).groups().get("user"));
        assertEquals("dana", allMatches.get(3).groups().get("user"));
        assertEquals("erin", allMatches.get(4).groups().get("user"));
    }

    @Test
    void matchesWwwTwitterUrls() {
        FxTwitterSite site = new FxTwitterSite(new FakeFxTwitterGateway(), config(1024));

        List<SaucyMatch> matches = site.match("https://www.twitter.com/alice/status/123", 10);

        assertEquals(1, matches.size());
        assertEquals("alice", matches.getFirst().groups().get("user"));
        assertEquals("123", matches.getFirst().groups().get("id"));
    }

    @Test
    void ignoresPhotoAndVideoSuffixesWhenMatching() {
        FxTwitterSite site = new FxTwitterSite(new FakeFxTwitterGateway(), config(1024));
        String content = """
                https://twitter.com/alice/status/123/photo/1
                https://x.com/bob/status/456/video/1
                """;

        List<SaucyMatch> matches = site.match(content, 10);

        assertEquals(2, matches.size());
        assertEquals("alice", matches.get(0).groups().get("user"));
        assertEquals("123", matches.get(0).groups().get("id"));
        assertEquals("", matches.get(0).groups().get("translate"));
        assertEquals("bob", matches.get(1).groups().get("user"));
        assertEquals("456", matches.get(1).groups().get("id"));
        assertEquals("", matches.get(1).groups().get("translate"));
    }

    @Test
    void capturesTranslateAfterMediaSuffix() {
        FxTwitterSite site = new FxTwitterSite(new FakeFxTwitterGateway(), config(1024));

        List<SaucyMatch> matches = site.match("https://twitter.com/alice/status/123/photo/1/en", 10);

        assertEquals(1, matches.size());
        assertEquals("alice", matches.getFirst().groups().get("user"));
        assertEquals("123", matches.getFirst().groups().get("id"));
        assertEquals("en", matches.getFirst().groups().get("translate"));
    }

    @Test
    void capturesTwoAndFiveCharacterTranslateCodes() {
        FxTwitterSite site = new FxTwitterSite(new FakeFxTwitterGateway(), config(1024));
        String content = """
                https://twitter.com/alice/status/123/en
                https://twitter.com/bob/status/456/photo/1/pt_BR
                """;

        List<SaucyMatch> matches = site.match(content, 10);

        assertEquals(2, matches.size());
        assertEquals("en", matches.get(0).groups().get("translate"));
        assertEquals("pt_BR", matches.get(1).groups().get("translate"));
    }

    @Test
    void doesNotMatchInvalidStatusSuffixes() {
        FxTwitterSite site = new FxTwitterSite(new FakeFxTwitterGateway(), config(1024));
        String content = """
                https://twitter.com/alice/status/123abc
                https://twitter.com/alice/status/123/english
                https://twitter.com/alice/status/123/photo/1/extra
                """;

        List<SaucyMatch> matches = site.match(content, 10);

        assertEquals(0, matches.size());
    }

    @Test
    void createsEmbedForRegularTweet() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        gateway.response(tweet("123", "Hello world", false, List.of(), List.of(), null));
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals(1, response.embeds().size());
        assertTrue(response.files().isEmpty());
        assertEquals(null, response.text());
        assertFalse(response.sensitive());
        MessageEmbed embed = response.embeds().getFirst();
        assertEquals(0x1DA1F2, embed.getColorRaw());
        assertEquals("Alice (@alice)", embed.getAuthor().getName());
        assertEquals("https://cdn.example/avatar.png", embed.getAuthor().getIconUrl());
        assertEquals("https://twitter.com/alice", embed.getAuthor().getUrl());
        assertEquals("Hello world", embed.getDescription());
        assertEquals("Twitter", embed.getFooter().getText());
        assertEquals(4, embed.getFields().size());
        assertEquals("Replies", embed.getFields().get(0).getName());
        assertEquals("0", embed.getFields().get(0).getValue());
        assertEquals("Retweets", embed.getFields().get(1).getName());
        assertEquals("0", embed.getFields().get(1).getValue());
        assertEquals("Likes", embed.getFields().get(2).getName());
        assertEquals("0", embed.getFields().get(2).getValue());
        assertEquals("Views", embed.getFields().get(3).getName());
        assertEquals("0", embed.getFields().get(3).getValue());
    }

    @Test
    void truncatesLongTweetDescription() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        gateway.response(tweet("123", "x".repeat(5_000), false, List.of(), List.of(), null));
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        String description = response.embeds().getFirst().getDescription();
        assertEquals(4_096, description.length());
        assertTrue(description.endsWith("..."));
    }

    @Test
    void truncatesLongTranslatedDescription() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        FxTwitterTranslation translation = new FxTwitterTranslation("t".repeat(5_000), "ja", "fr");
        gateway.response(tweet("123", "Original", false, List.of(), List.of(), translation));
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", "fr")).join().orElseThrow();

        String description = response.embeds().getFirst().getDescription();
        assertEquals(4_096, description.length());
        assertTrue(description.endsWith("..."));
    }

    @Test
    void createsEmbedPerPhoto() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        List<FxTwitterPhoto> photos = List.of(
                new FxTwitterPhoto("photo", "https://pbs.twimg.com/media/one.jpg?format=jpg&name=small", 800, 600),
                new FxTwitterPhoto("photo", "https://pbs.twimg.com/media/two.jpg", 800, 600)
        );
        gateway.response(tweet("123", "Photo tweet", false, photos, List.of(), null));
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals(2, response.embeds().size());
        assertTrue(response.files().isEmpty());
        assertTrue(response.embeds().get(0).getImage().getUrl().contains("name=orig"));
        assertTrue(response.embeds().get(1).getImage().getUrl().contains("name=orig"));
    }

    @Test
    void ignoresInvalidPhotoUrls() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        List<FxTwitterPhoto> photos = List.of(
                new FxTwitterPhoto("photo", "", 800, 600),
                new FxTwitterPhoto("photo", "ftp://pbs.twimg.com/media/invalid.jpg", 800, 600),
                new FxTwitterPhoto("photo", "https://pbs.twimg.com/media/valid.jpg", 800, 600)
        );
        gateway.response(tweet("123", "Photo tweet", false, photos, List.of(), null));
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals(1, response.embeds().size());
        assertEquals("https://pbs.twimg.com/media/valid.jpg?name=orig", response.embeds().getFirst().getImage().getUrl());
    }

    @Test
    void fallsBackToRegularEmbedWhenAllPhotoUrlsAreInvalid() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        List<FxTwitterPhoto> photos = List.of(
                new FxTwitterPhoto("photo", "", 800, 600),
                new FxTwitterPhoto("photo", "file:///tmp/invalid.jpg", 800, 600)
        );
        gateway.response(tweet("123", "Photo tweet", false, photos, List.of(), null));
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals(1, response.embeds().size());
        assertEquals("Photo tweet", response.embeds().getFirst().getDescription());
        assertEquals(null, response.embeds().getFirst().getImage());
    }

    @Test
    void marksSensitiveTweets() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        gateway.response(tweet("123", "Sensitive", true, List.of(), List.of(), null));
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertTrue(response.sensitive());
    }

    @Test
    void fallsBackToFxTwitterWhenVideoTooLarge() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        String videoUrl = "https://video.example/path/tweet-video.mp4";
        gateway.response(tweet("123", "Video tweet", false, List.of(), List.of(video(videoUrl)), null));
        gateway.length(videoUrl, 2048);
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals("https://fxtwitter.com/alice/status/123", response.text());
        assertTrue(response.embeds().isEmpty());
        assertTrue(response.files().isEmpty());
    }

    @Test
    void fallsBackWhenVideoDownloadFails() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        String videoUrl = "https://video.example/path/tweet-video.mp4";
        gateway.response(tweet("123", "Video tweet", false, List.of(), List.of(video(videoUrl)), null));
        gateway.length(videoUrl, 512);
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals("https://fxtwitter.com/alice/status/123", response.text());
        assertTrue(response.embeds().isEmpty());
        assertTrue(response.files().isEmpty());
    }

    @Test
    void fallsBackWhenDownloadedVideoExceedsLimit() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        String videoUrl = "https://video.example/path/tweet-video.mp4";
        gateway.response(tweet("123", "Video tweet", false, List.of(), List.of(video(videoUrl)), null));
        gateway.length(videoUrl, 0);
        gateway.download(videoUrl, new byte[]{1, 2, 3, 4, 5});
        FxTwitterSite site = new FxTwitterSite(gateway, config(4));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals("https://fxtwitter.com/alice/status/123", response.text());
        assertTrue(response.embeds().isEmpty());
        assertTrue(response.files().isEmpty());
    }

    @Test
    void uploadsVideoWhenSmallEnough() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        String videoUrl = "https://video.example/path/tweet-video.mp4";
        byte[] videoBytes = new byte[]{1, 2, 3};
        gateway.response(tweet("123", "Video tweet", false, List.of(), List.of(video(videoUrl)), null));
        gateway.length(videoUrl, videoBytes.length);
        gateway.download(videoUrl, videoBytes);
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals(1, response.embeds().size());
        assertEquals(1, response.files().size());
        SaucyFileAttachment file = response.files().getFirst();
        assertEquals("tweet-video.mp4", file.fileName());
        assertArrayEquals(videoBytes, file.data());
    }

    @Test
    void uploadsVideoWithUnknownHeadWhenDownloadedBytesAreSmall() {
        FakeFxTwitterGateway gateway = new FakeFxTwitterGateway();
        String videoUrl = "https://video.example/path/tweet-video.mp4";
        byte[] videoBytes = new byte[]{1, 2, 3};
        gateway.response(tweet("123", "Video tweet", false, List.of(), List.of(video(videoUrl)), null));
        gateway.length(videoUrl, 0);
        gateway.download(videoUrl, videoBytes);
        FxTwitterSite site = new FxTwitterSite(gateway, config(1024));

        SaucyProcessResponse response = site.process(match("alice", "123", null)).join().orElseThrow();

        assertEquals(1, response.embeds().size());
        assertEquals(1, response.files().size());
        assertArrayEquals(videoBytes, response.files().getFirst().data());
    }

    private static SaucyMatch match(String user, String id, String translate) {
        return new SaucyMatch("fxtwitter", "https://twitter.com/" + user + "/status/" + id, Map.of(
                "user", user,
                "id", id,
                "translate", translate == null ? "" : translate
        ));
    }

    private static FxTwitterResponse tweet(
            String id,
            String text,
            boolean sensitive,
            List<FxTwitterPhoto> photos,
            List<FxTwitterVideo> videos,
            FxTwitterTranslation translation
    ) {
        return new FxTwitterResponse(new FxTwitterTweet(
                id,
                "https://twitter.com/alice/status/" + id,
                text,
                1_735_689_600L,
                new FxTwitterAuthor("42", "Alice", "alice", "https://cdn.example/avatar.png", null),
                null,
                null,
                null,
                null,
                sensitive,
                new FxTwitterMedia(photos, videos),
                translation,
                null
        ));
    }

    private static FxTwitterVideo video(String url) {
        return new FxTwitterVideo("video", url, "https://video.example/thumb.jpg", 1280, 720, "mp4");
    }

    private static SaucyLinkEmbedConfig config(long maxFileBytes) {
        return new SaucyLinkEmbedConfig(
                true,
                3600,
                8,
                4,
                maxFileBytes,
                true,
                "Traitement du lien en cours...",
                "",
                5,
                "mp4",
                2000,
                List.of("misskey.io")
        );
    }

    private static final class FakeFxTwitterGateway implements FxTwitterGateway {
        private FxTwitterResponse response;
        private final Map<String, Long> lengths = new java.util.HashMap<>();
        private final Map<String, byte[]> downloads = new java.util.HashMap<>();

        private void response(FxTwitterResponse response) {
            this.response = response;
        }

        private void length(String url, long length) {
            lengths.put(url, length);
        }

        private void download(String url, byte[] bytes) {
            downloads.put(url, bytes);
        }

        @Override
        public Optional<FxTwitterResponse> getTweet(String user, String id, String translate) {
            return Optional.ofNullable(response);
        }

        @Override
        public long contentLength(String url) {
            return lengths.getOrDefault(url, 0L);
        }

        @Override
        public byte[] download(String url, long maxBytes) {
            return downloads.getOrDefault(url, new byte[0]);
        }
    }
}
