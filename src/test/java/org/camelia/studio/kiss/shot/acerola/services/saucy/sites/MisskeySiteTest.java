package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMatch;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyProcessResponse;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyFileAttachment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisskeySiteTest {
    @Test
    void matchesConfiguredMisskeyDomainsAndRespectsRemainingSlots() {
        MisskeySite site = new MisskeySite(new FakeMisskeyGateway(), config(List.of(
                "misskey.io",
                "misskey.design",
                "oekakiskey.com"
        )));
        String content = """
                https://misskey.io/notes/abc123
                https://www.misskey.design/notes/def456?with=query
                https://oekakiskey.com/notes/999aaa
                https://unknown.example/notes/nope
                """;

        List<SaucyMatch> limitedMatches = site.match(content, 2);
        List<SaucyMatch> allMatches = site.match(content, 10);

        assertEquals(2, limitedMatches.size());
        assertEquals(3, allMatches.size());
        assertEquals("misskey", allMatches.getFirst().siteId());
        assertEquals("abc123", allMatches.getFirst().groups().get("id"));
        assertEquals("https://misskey.io", allMatches.getFirst().groups().get("baseUrl"));
        assertEquals("def456", allMatches.get(1).groups().get("id"));
        assertEquals("https://misskey.design", allMatches.get(1).groups().get("baseUrl"));
        assertEquals("999aaa", allMatches.get(2).groups().get("id"));
        assertEquals("https://oekakiskey.com", allMatches.get(2).groups().get("baseUrl"));
    }

    @Test
    void ignoresUnknownDomainsAndInvalidNoteIds() {
        MisskeySite site = new MisskeySite(new FakeMisskeyGateway(), config(List.of("misskey.io")));
        String content = """
                https://example.com/notes/abc123
                https://misskey.io/notes/ABC123
                https://misskey.io/notes/abc123/extra
                """;

        List<SaucyMatch> matches = site.match(content, 10);

        assertEquals(0, matches.size());
    }

    @Test
    void createsSingleEmbedWithImageAttachments() {
        FakeMisskeyGateway gateway = new FakeMisskeyGateway();
        gateway.download("https://cdn.example/one.png", new byte[]{1});
        gateway.download("https://cdn.example/two-thumb.jpg", new byte[]{2});
        gateway.note(note("abc123", "Bonjour Misskey", List.of(
                file("1", "image/png", false, "https://cdn.example/one.png", "https://cdn.example/one-thumb.png"),
                file("2", "image/jpeg", false, "", "https://cdn.example/two-thumb.jpg"),
                file("3", "video/mp4", false, "https://cdn.example/video.mp4", "https://cdn.example/video-thumb.jpg")
        )));
        MisskeySite site = new MisskeySite(gateway, config(List.of("misskey.io")));

        SaucyProcessResponse response = site.process(match("https://misskey.io", "abc123")).join().orElseThrow();

        assertNull(response.text());
        assertFalse(response.sensitive());
        assertEquals(1, response.embeds().size());
        assertEquals(2, response.files().size());
        MessageEmbed first = response.embeds().getFirst();
        assertEquals(0x85B300, first.getColorRaw());
        assertEquals("https://misskey.io/notes/abc123", first.getUrl());
        assertEquals("Alice (@alice)", first.getAuthor().getName());
        assertEquals("https://misskey.io/notes/abc123", first.getAuthor().getUrl());
        assertEquals("https://cdn.example/avatar.png", first.getAuthor().getIconUrl());
        assertEquals("Bonjour Misskey", first.getDescription());
        assertNull(first.getImage());
        assertEquals("Misskey", first.getFooter().getText());
        assertEquals(OffsetDateTime.parse("2025-01-01T00:00:00Z"), first.getTimestamp());

        SaucyFileAttachment firstFile = response.files().getFirst();
        SaucyFileAttachment secondFile = response.files().get(1);
        assertEquals("one.png", firstFile.fileName());
        assertEquals("image/png", firstFile.contentType());
        assertArrayEquals(new byte[]{1}, firstFile.data());
        assertEquals("two-thumb.jpg", secondFile.fileName());
        assertEquals("image/jpeg", secondFile.contentType());
        assertArrayEquals(new byte[]{2}, secondFile.data());
    }

    @Test
    void returnsEmptyWhenThereAreOnlyNonImageFiles() {
        FakeMisskeyGateway gateway = new FakeMisskeyGateway();
        gateway.note(note("abc123", "Texte seul", List.of(
                file("1", "video/mp4", true, "https://cdn.example/video.mp4", "https://cdn.example/video-thumb.jpg")
        )));
        MisskeySite site = new MisskeySite(gateway, config(List.of("misskey.io")));

        Optional<SaucyProcessResponse> response = site.process(match("https://misskey.io", "abc123")).join();

        assertTrue(response.isEmpty());
    }

    @Test
    void marksResponseSensitiveWhenAnyImageFileIsSensitive() {
        FakeMisskeyGateway gateway = new FakeMisskeyGateway();
        gateway.download("https://cdn.example/image.png", new byte[]{1});
        gateway.note(note("abc123", "Image sensible", List.of(
                file("1", "image/png", true, "https://cdn.example/image.png", "https://cdn.example/image-thumb.png")
        )));
        MisskeySite site = new MisskeySite(gateway, config(List.of("misskey.io")));

        SaucyProcessResponse response = site.process(match("https://misskey.io", "abc123")).join().orElseThrow();

        assertTrue(response.sensitive());
        assertEquals(1, response.embeds().size());
        assertEquals(1, response.files().size());
    }

    @Test
    void returnsEmptyWhenAllImageUrlsAreInvalid() {
        FakeMisskeyGateway gateway = new FakeMisskeyGateway();
        gateway.note(note("abc123", "Images invalides", List.of(
                file("1", "image/png", false, "", ""),
                file("2", "image/jpeg", false, "file:///tmp/local.jpg", "ftp://cdn.example/thumb.jpg")
        )));
        MisskeySite site = new MisskeySite(gateway, config(List.of("misskey.io")));

        Optional<SaucyProcessResponse> response = site.process(match("https://misskey.io", "abc123")).join();

        assertTrue(response.isEmpty());
    }

    @Test
    void truncatesLongDescriptionsToDiscordLimit() {
        FakeMisskeyGateway gateway = new FakeMisskeyGateway();
        gateway.download("https://cdn.example/image.png", new byte[]{1});
        gateway.note(note("abc123", "x".repeat(5_000), List.of(
                file("1", "image/png", false, "https://cdn.example/image.png", "")
        )));
        MisskeySite site = new MisskeySite(gateway, config(List.of("misskey.io")));

        SaucyProcessResponse response = site.process(match("https://misskey.io", "abc123")).join().orElseThrow();

        String description = response.embeds().getFirst().getDescription();
        assertEquals(4_096, description.length());
        assertTrue(description.endsWith("..."));
    }

    @Test
    void returnsEmptyWhenClientHasNoUsableNoteOrThrows() {
        FakeMisskeyGateway emptyGateway = new FakeMisskeyGateway();
        MisskeySite emptySite = new MisskeySite(emptyGateway, config(List.of("misskey.io")));
        FakeMisskeyGateway throwingGateway = new FakeMisskeyGateway();
        throwingGateway.throwOnGet();
        MisskeySite throwingSite = new MisskeySite(throwingGateway, config(List.of("misskey.io")));

        Optional<SaucyProcessResponse> emptyResponse = emptySite.process(match("https://misskey.io", "abc123")).join();
        Optional<SaucyProcessResponse> throwingResponse = throwingSite.process(match("https://misskey.io", "abc123")).join();

        assertTrue(emptyResponse.isEmpty());
        assertTrue(throwingResponse.isEmpty());
    }

    private static SaucyMatch match(String baseUrl, String id) {
        return new SaucyMatch("misskey", baseUrl + "/notes/" + id, Map.of(
                "baseUrl", baseUrl,
                "id", id
        ));
    }

    private static MisskeyNote note(String id, String text, List<MisskeyFile> files) {
        return new MisskeyNote(
                id,
                Instant.parse("2025-01-01T00:00:00Z").toString(),
                text,
                "public",
                files,
                new MisskeyUser("u1", "Alice", "alice", "https://cdn.example/avatar.png")
        );
    }

    private static MisskeyFile file(
            String id,
            String type,
            boolean sensitive,
            String url,
            String thumbnailUrl
    ) {
        return new MisskeyFile(id, type, 1234, sensitive, url, thumbnailUrl);
    }

    private static SaucyLinkEmbedConfig config(List<String> misskeyDomains) {
        return new SaucyLinkEmbedConfig(
                true,
                3600,
                8,
                4,
                10_485_760L,
                true,
                "Traitement du lien en cours...",
                "",
                5,
                "mp4",
                2000,
                misskeyDomains
        );
    }

    private static final class FakeMisskeyGateway implements MisskeyGateway {
        private MisskeyNote note;
        private boolean throwOnGet;
        private final Map<String, Long> lengths = new HashMap<>();
        private final Map<String, byte[]> downloads = new HashMap<>();

        private void note(MisskeyNote note) {
            this.note = note;
        }

        private void throwOnGet() {
            throwOnGet = true;
        }

        private void length(String url, long length) {
            lengths.put(url, length);
        }

        private void download(String url, byte[] bytes) {
            downloads.put(url, bytes);
        }

        @Override
        public Optional<MisskeyNote> getNote(String baseUrl, String id) {
            if (throwOnGet) {
                throw new IllegalStateException("boom");
            }

            return Optional.ofNullable(note);
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
