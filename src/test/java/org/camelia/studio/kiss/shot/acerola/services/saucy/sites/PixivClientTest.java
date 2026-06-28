package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixivClientTest {
    @Test
    void reportsWhetherSessionCookieIsConfigured() {
        assertTrue(client(new RecordingHttpClient(), "session").hasSessionCookie());
        assertFalse(client(new RecordingHttpClient(), "   ").hasSessionCookie());
    }

    @Test
    void fetchesDetailsWithPixivHeadersAndCachesUsableJson() {
        RecordingHttpClient httpClient = new RecordingHttpClient(response(200, illustrationJson("106848609", 0, 1, 0)));
        PixivClient client = client(httpClient, "abc123");

        Optional<PixivIllustrationResponse> first = client.illustrationDetails("106848609");
        Optional<PixivIllustrationResponse> second = client.illustrationDetails("106848609");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals("106848609", second.orElseThrow().body().id());
        assertEquals(1, httpClient.requestCount());
        HttpRequest request = httpClient.requests().getFirst();
        assertEquals("GET", request.method());
        assertEquals(URI.create("https://www.pixiv.net/ajax/illust/106848609"), request.uri());
        assertPixivHeaders(request);
    }

    @Test
    void fetchesPagesAndDoesNotCacheUnusableJson() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(200, "<html>temporarily unavailable</html>"),
                response(200, "{\"error\":false,\"message\":\"\",\"body\":null}"),
                response(200, pagesJson())
        );
        PixivClient client = client(httpClient, "abc123");

        Optional<PixivPagesResponse> malformed = client.illustrationPages("106848609");
        Optional<PixivPagesResponse> nullBody = client.illustrationPages("106848609");
        Optional<PixivPagesResponse> recovered = client.illustrationPages("106848609");

        assertTrue(malformed.isEmpty());
        assertTrue(nullBody.isEmpty());
        assertTrue(recovered.isPresent());
        assertEquals(1, recovered.orElseThrow().body().size());
        assertEquals(3, httpClient.requestCount());
        assertEquals(URI.create("https://www.pixiv.net/ajax/illust/106848609/pages"), httpClient.requests().get(2).uri());
        assertPixivHeaders(httpClient.requests().get(2));
    }

    @Test
    void fetchesUgoiraMetadataWithPixivHeadersAndCachesUsableJson() {
        RecordingHttpClient httpClient = new RecordingHttpClient(response(200, ugoiraMetadataJson()));
        PixivClient client = client(httpClient, "abc123");

        Optional<PixivUgoiraMetadataResponse> first = client.ugoiraMetadata("106848609");
        Optional<PixivUgoiraMetadataResponse> second = client.ugoiraMetadata("106848609");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals("https://i.pximg.net/img-zip-ugoira/106848609_ugoira1920x1080.zip",
                second.orElseThrow().body().originalSrc());
        assertEquals(2, second.orElseThrow().body().frames().size());
        assertEquals(1, httpClient.requestCount());
        HttpRequest request = httpClient.requests().getFirst();
        assertEquals("GET", request.method());
        assertEquals(URI.create("https://www.pixiv.net/ajax/illust/106848609/ugoira_meta"), request.uri());
        assertPixivHeaders(request);
    }

    @Test
    void ugoiraMetadataReturnsEmptyAndDoesNotCacheUnusableJson() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(500, ugoiraMetadataJson()),
                response(200, "<html>temporarily unavailable</html>"),
                response(200, "{\"error\":true,\"message\":\"private\",\"body\":null}"),
                response(200, "{\"error\":false,\"message\":\"\",\"body\":null}"),
                response(200, ugoiraMetadataJson())
        );
        PixivClient client = client(httpClient, "abc123");

        Optional<PixivUgoiraMetadataResponse> nonSuccess = client.ugoiraMetadata("106848609");
        Optional<PixivUgoiraMetadataResponse> malformed = client.ugoiraMetadata("106848609");
        Optional<PixivUgoiraMetadataResponse> error = client.ugoiraMetadata("106848609");
        Optional<PixivUgoiraMetadataResponse> nullBody = client.ugoiraMetadata("106848609");
        Optional<PixivUgoiraMetadataResponse> recovered = client.ugoiraMetadata("106848609");

        assertTrue(nonSuccess.isEmpty());
        assertTrue(malformed.isEmpty());
        assertTrue(error.isEmpty());
        assertTrue(nullBody.isEmpty());
        assertTrue(recovered.isPresent());
        assertEquals(5, httpClient.requestCount());
        assertEquals(URI.create("https://www.pixiv.net/ajax/illust/106848609/ugoira_meta"),
                httpClient.requests().get(4).uri());
        assertPixivHeaders(httpClient.requests().get(4));
    }

    @Test
    void downloadReadsStreamWithPixivHeadersAndMaxBytesCap() {
        byte[] smallBytes = new byte[]{1, 2, 3};
        byte[] oversizedBytes = new byte[]{1, 2, 3, 4, 5};
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(200, new ByteArrayInputStream(smallBytes)),
                response(200, new ByteArrayInputStream(oversizedBytes))
        );
        PixivClient client = client(httpClient, "abc123");

        Optional<byte[]> small = client.download("https://i.pximg.net/img-original/image.png", 3);
        Optional<byte[]> oversized = client.download("https://i.pximg.net/img-original/large.png", 4);

        assertTrue(small.isPresent());
        assertArrayEquals(smallBytes, small.orElseThrow());
        assertTrue(oversized.isEmpty());
        assertEquals(2, httpClient.requestCount());
        assertPixivHeaders(httpClient.requests().getFirst());
        assertPixivHeaders(httpClient.requests().get(1));
    }

    @Test
    void contentLengthUsesHeadAndReturnsZeroWhenUnavailable() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(200, null, Map.of("content-length", List.of("1234"))),
                response(404, null, Map.of("content-length", List.of("9999"))),
                response(200, null)
        );
        PixivClient client = client(httpClient, "abc123");

        long known = client.contentLength("https://i.pximg.net/img-original/image.png");
        long missing = client.contentLength("https://i.pximg.net/img-original/missing.png");
        long unknown = client.contentLength("https://i.pximg.net/img-original/unknown.png");

        assertEquals(1234L, known);
        assertEquals(0L, missing);
        assertEquals(0L, unknown);
        assertEquals("HEAD", httpClient.requests().getFirst().method());
        assertPixivHeaders(httpClient.requests().getFirst());
    }

    private static PixivClient client(HttpClient httpClient, String cookie) {
        return new PixivClient(
                httpClient,
                new ObjectMapper(),
                new SaucyLinkCache<>(Duration.ofMinutes(10), () -> 1_000L),
                cookie
        );
    }

    private static void assertPixivHeaders(HttpRequest request) {
        assertEquals(Duration.ofSeconds(15), request.timeout().orElseThrow());
        assertEquals(Optional.of("PHPSESSID=abc123"), request.headers().firstValue("Cookie"));
        assertEquals(Optional.of("https://www.pixiv.net/"), request.headers().firstValue("Referer"));
        assertTrue(request.headers().firstValue("User-Agent").orElse("").contains("Mozilla"));
    }

    private static HttpResponse<String> response(int statusCode, String body) {
        return response(statusCode, body, Map.of());
    }

    private static <T> HttpResponse<T> response(int statusCode, T body) {
        return response(statusCode, body, Map.of());
    }

    private static <T> HttpResponse<T> response(int statusCode, T body, Map<String, List<String>> headers) {
        return new FakeHttpResponse<>(statusCode, body, HttpHeaders.of(headers, (name, value) -> true));
    }

    private static String illustrationJson(String id, int illustType, int pageCount, int xRestrict) {
        return """
                {
                  "error": false,
                  "message": "",
                  "body": {
                    "id": "%s",
                    "title": "Title",
                    "description": "Description",
                    "illustType": %d,
                    "pageCount": %d,
                    "xRestrict": %d,
                    "urls": {
                      "mini": "https://i.pximg.net/c/48x48/img-master/img/%s_p0_square1200.jpg",
                      "thumb": "https://i.pximg.net/c/250x250_80_a2/img-master/img/%s_p0_square1200.jpg",
                      "small": "https://i.pximg.net/c/540x540_70/img-master/img/%s_p0_master1200.jpg",
                      "regular": "https://i.pximg.net/img-master/img/%s_p0_master1200.jpg",
                      "original": "https://i.pximg.net/img-original/img/%s_p0.png"
                    }
                  }
                }
                """.formatted(id, illustType, pageCount, xRestrict, id, id, id, id, id);
    }

    private static String pagesJson() {
        return """
                {
                  "error": false,
                  "message": "",
                  "body": [
                    {
                      "urls": {
                        "thumb_mini": "https://i.pximg.net/c/48x48/img-master/img/106848609_p0_square1200.jpg",
                        "small": "https://i.pximg.net/c/540x540_70/img-master/img/106848609_p0_master1200.jpg",
                        "regular": "https://i.pximg.net/img-master/img/106848609_p0_master1200.jpg",
                        "original": "https://i.pximg.net/img-original/img/106848609_p0.png"
                      },
                      "width": 1200,
                      "height": 1600
                    }
                  ]
                }
                """;
    }

    private static String ugoiraMetadataJson() {
        return """
                {
                  "error": false,
                  "message": "",
                  "body": {
                    "originalSrc": "https://i.pximg.net/img-zip-ugoira/106848609_ugoira1920x1080.zip",
                    "src": "https://i.pximg.net/img-zip-ugoira/106848609_ugoira600x600.zip",
                    "mime_type": "image/jpeg",
                    "frames": [
                      { "file": "000000.jpg", "delay": 60 },
                      { "file": "000001.jpg", "delay": 60 }
                    ]
                  }
                }
                """;
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final ArrayDeque<HttpResponse<?>> responses;
        private final List<HttpRequest> requests = new java.util.ArrayList<>();

        private RecordingHttpClient(HttpResponse<?>... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        private int requestCount() {
            return requests.size();
        }

        private List<HttpRequest> requests() {
            return requests;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            requests.add(request);
            HttpResponse<?> response = responses.pollFirst();
            if (response == null) {
                throw new AssertionError("No fake response queued for " + request.uri());
            }

            return (HttpResponse<T>) response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException("sendAsync is not used by PixivClient");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException("sendAsync is not used by PixivClient");
        }
    }

    private record FakeHttpResponse<T>(int statusCode, T body, HttpHeaders headers) implements HttpResponse<T> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://www.pixiv.net/");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
