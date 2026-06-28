package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyFileAttachment;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMatch;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyProcessResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixivSiteTest {
    @Test
    void matchesArtworkUrlsAndRespectsRemainingSlots() {
        PixivSite site = new PixivSite(new FakePixivGateway(), config("", 1024, 5));
        String content = """
                https://www.pixiv.net/en/artworks/106848609
                https://pixiv.net/artworks/42?foo=bar
                http://www.pixiv.net/users/12/artworks/777/
                https://www.pixiv.net/en/artworks/123abc
                https://www.pixiv.net/en/artworks/456/extra
                https://example.com/en/artworks/999
                """;

        List<SaucyMatch> limitedMatches = site.match(content, 2);
        List<SaucyMatch> allMatches = site.match(content, 10);

        assertEquals(2, limitedMatches.size());
        assertEquals(3, allMatches.size());
        assertEquals("pixiv", allMatches.getFirst().siteId());
        assertEquals("106848609", allMatches.getFirst().groups().get("id"));
        assertEquals("42", allMatches.get(1).groups().get("id"));
        assertEquals("777", allMatches.get(2).groups().get("id"));
    }

    @Test
    void returnsEmptyWithoutSessionCookieAndDoesNotFetchAnything() {
        FakePixivGateway gateway = new FakePixivGateway(false);
        PixivSite site = new PixivSite(gateway, config("", 1024, 5));

        Optional<SaucyProcessResponse> response = site.process(match("106848609")).join();

        assertTrue(response.isEmpty());
        assertEquals(0, gateway.detailsRequests);
        assertEquals(0, gateway.pagesRequests);
        assertEquals(0, gateway.downloadRequests);
    }

    @Test
    void singlePageIllustrationProducesOneFile() {
        FakePixivGateway gateway = new FakePixivGateway();
        String original = "https://i.pximg.net/img-original/img/2023/04/01/00/00/00/106848609_p0.png";
        byte[] bytes = new byte[]{1, 2, 3};
        gateway.details(illustration("106848609", 0, 1, 0, urls(original, "regular", "small", "thumb")));
        gateway.length(original, bytes.length);
        gateway.download(original, bytes);
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        SaucyProcessResponse response = site.process(match("106848609")).join().orElseThrow();

        assertEquals(null, response.text());
        assertTrue(response.embeds().isEmpty());
        assertFalse(response.sensitive());
        assertEquals(1, response.files().size());
        SaucyFileAttachment file = response.files().getFirst();
        assertEquals("106848609_p0.png", file.fileName());
        assertEquals("image/png", file.contentType());
        assertArrayEquals(bytes, file.data());
        assertEquals(0, gateway.pagesRequests);
    }

    @Test
    void fourPageIllustrationProducesFourFiles() {
        FakePixivGateway gateway = new FakePixivGateway();
        gateway.details(illustration("106848609", 0, 4, 0, urls("single-original", "regular", "small", "thumb")));
        for (int index = 0; index < 4; index++) {
            String url = imageUrl(index, "jpg");
            gateway.page(page(url, url + "?regular", url + "?small", url + "?thumb"));
            gateway.length(url, 10);
            gateway.download(url, new byte[]{(byte) index});
        }
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        SaucyProcessResponse response = site.process(match("106848609")).join().orElseThrow();

        assertEquals(null, response.text());
        assertFalse(response.sensitive());
        assertEquals(4, response.files().size());
        assertEquals("106848609_p0.jpg", response.files().get(0).fileName());
        assertEquals("106848609_p3.jpg", response.files().get(3).fileName());
        assertEquals(1, gateway.pagesRequests);
    }

    @Test
    void tenPageIllustrationRespectsImageLimitAndAddsSetText() {
        FakePixivGateway gateway = new FakePixivGateway();
        gateway.details(illustration("106848609", 0, 10, 0, urls("single-original", "regular", "small", "thumb")));
        for (int index = 0; index < 10; index++) {
            String url = imageUrl(index, "png");
            gateway.page(page(url, url + "?regular", url + "?small", url + "?thumb"));
            gateway.length(url, 10);
            gateway.download(url, new byte[]{(byte) index});
        }
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        SaucyProcessResponse response = site.process(match("106848609")).join().orElseThrow();

        assertEquals("This is part of a 10 image set.", response.text());
        assertEquals(5, response.files().size());
        assertEquals("106848609_p4.png", response.files().get(4).fileName());
    }

    @Test
    void marksXRestrictedIllustrationAsSensitive() {
        FakePixivGateway gateway = new FakePixivGateway();
        String original = imageUrl(0, "jpg");
        gateway.details(illustration("106848609", 0, 1, 1, urls(original, "regular", "small", "thumb")));
        gateway.length(original, 10);
        gateway.download(original, new byte[]{1});
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        SaucyProcessResponse response = site.process(match("106848609")).join().orElseThrow();

        assertTrue(response.sensitive());
        assertEquals(1, response.files().size());
    }

    @Test
    void triesLowerQualityWhenOriginalIsTooLargeAndSkipsFilesWithoutUsableCandidate() {
        FakePixivGateway gateway = new FakePixivGateway();
        gateway.details(illustration("106848609", 0, 2, 0, urls("single-original", "regular", "small", "thumb")));
        String largeOriginal = imageUrl(0, "png");
        String regular = "https://i.pximg.net/img-master/img/2023/04/01/00/00/00/106848609_p0_master1200.jpg";
        String tooLargeOriginal = imageUrl(1, "png");
        String tooLargeRegular = "https://i.pximg.net/img-master/img/2023/04/01/00/00/00/106848609_p1_master1200.jpg";
        gateway.page(page(largeOriginal, regular, "", ""));
        gateway.page(page(tooLargeOriginal, tooLargeRegular, "", ""));
        gateway.length(largeOriginal, 2048);
        gateway.length(regular, 10);
        gateway.length(tooLargeOriginal, 2048);
        gateway.length(tooLargeRegular, 2048);
        gateway.download(regular, new byte[]{7});
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        SaucyProcessResponse response = site.process(match("106848609")).join().orElseThrow();

        assertEquals(1, response.files().size());
        assertEquals("106848609_p0_master1200.jpg", response.files().getFirst().fileName());
        assertArrayEquals(new byte[]{7}, response.files().getFirst().data());
        assertFalse(gateway.downloads.containsKey(largeOriginal));
        assertFalse(gateway.downloads.containsKey(tooLargeOriginal));
        assertFalse(gateway.downloads.containsKey(tooLargeRegular));
    }

    @Test
    void fallsBackWhenHigherQualityUrlsAreMissing() {
        FakePixivGateway gateway = new FakePixivGateway();
        String regular = "https://i.pximg.net/img-master/img/2023/04/01/00/00/00/106848609_p0_master1200.jpg";
        gateway.details(illustration("106848609", 0, 1, 0, urls(null, regular, null, null)));
        gateway.length(regular, 10);
        gateway.download(regular, new byte[]{9});
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        SaucyProcessResponse response = site.process(match("106848609")).join().orElseThrow();

        assertEquals(1, response.files().size());
        assertEquals("106848609_p0_master1200.jpg", response.files().getFirst().fileName());
        assertArrayEquals(new byte[]{9}, response.files().getFirst().data());
    }

    @Test
    void returnsEmptyWhenNoFilesCanBeDownloaded() {
        FakePixivGateway gateway = new FakePixivGateway();
        String original = imageUrl(0, "jpg");
        gateway.details(illustration("106848609", 0, 1, 0, urls(original, "", "", "")));
        gateway.length(original, 0);
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        Optional<SaucyProcessResponse> response = site.process(match("106848609")).join();

        assertTrue(response.isEmpty());
    }

    @Test
    void returnsEmptyForUgoiraUntilTaskSeven() {
        FakePixivGateway gateway = new FakePixivGateway();
        gateway.details(illustration("106848609", 2, 1, 0, urls(imageUrl(0, "jpg"), "", "", "")));
        PixivSite site = new PixivSite(gateway, config("session", 1024, 5));

        Optional<SaucyProcessResponse> response = site.process(match("106848609")).join();

        assertTrue(response.isEmpty());
    }

    private static SaucyMatch match(String id) {
        return new SaucyMatch("pixiv", "https://www.pixiv.net/en/artworks/" + id, Map.of("id", id));
    }

    private static PixivIllustrationResponse illustration(
            String id,
            int illustType,
            int pageCount,
            int xRestrict,
            PixivIllustrationUrls urls
    ) {
        return new PixivIllustrationResponse(false, "", new PixivIllustration(
                id,
                "Title",
                "Description",
                illustType,
                pageCount,
                xRestrict,
                urls
        ));
    }

    private static PixivIllustrationUrls urls(String original, String regular, String small, String thumb) {
        return new PixivIllustrationUrls("mini", thumb, small, regular, original);
    }

    private static PixivPage page(String original, String regular, String small, String thumbMini) {
        return new PixivPage(new PixivPageUrls(thumbMini, small, regular, original), 1200, 1600);
    }

    private static String imageUrl(int page, String extension) {
        return "https://i.pximg.net/img-original/img/2023/04/01/00/00/00/106848609_p%d.%s"
                .formatted(page, extension);
    }

    private static SaucyLinkEmbedConfig config(String cookie, long maxFileBytes, int pixivImageLimit) {
        return new SaucyLinkEmbedConfig(
                true,
                3600,
                8,
                4,
                maxFileBytes,
                true,
                "Traitement du lien en cours...",
                cookie,
                pixivImageLimit,
                "mp4",
                2000,
                List.of("misskey.io")
        );
    }

    private static final class FakePixivGateway implements PixivGateway {
        private final boolean hasSessionCookie;
        private PixivIllustrationResponse details;
        private final List<PixivPage> pages = new java.util.ArrayList<>();
        private final Map<String, Long> lengths = new HashMap<>();
        private final Map<String, byte[]> downloads = new HashMap<>();
        private int detailsRequests;
        private int pagesRequests;
        private int downloadRequests;

        private FakePixivGateway() {
            this(true);
        }

        private FakePixivGateway(boolean hasSessionCookie) {
            this.hasSessionCookie = hasSessionCookie;
        }

        private void details(PixivIllustrationResponse details) {
            this.details = details;
        }

        private void page(PixivPage page) {
            pages.add(page);
        }

        private void length(String url, long length) {
            lengths.put(url, length);
        }

        private void download(String url, byte[] bytes) {
            downloads.put(url, bytes);
        }

        @Override
        public boolean hasSessionCookie() {
            return hasSessionCookie;
        }

        @Override
        public Optional<PixivIllustrationResponse> illustrationDetails(String id) {
            detailsRequests++;
            return Optional.ofNullable(details);
        }

        @Override
        public Optional<PixivPagesResponse> illustrationPages(String id) {
            pagesRequests++;
            return Optional.of(new PixivPagesResponse(false, "", pages));
        }

        @Override
        public long contentLength(String url) {
            return lengths.getOrDefault(url, 0L);
        }

        @Override
        public Optional<byte[]> download(String url, long maxBytes) {
            downloadRequests++;
            return Optional.ofNullable(downloads.get(url));
        }
    }
}
