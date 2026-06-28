package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisskeyClientTest {
    @Test
    void postsNoteIdJsonAndCachesParsedNotes() {
        RecordingHttpClient httpClient = new RecordingHttpClient(response(200, noteJson("abc123", "Cached")));
        MisskeyClient client = client(httpClient);

        Optional<MisskeyNote> first = client.getNote("https://misskey.io", "abc123");
        Optional<MisskeyNote> second = client.getNote("https://misskey.io", "abc123");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals("Cached", second.orElseThrow().text());
        assertEquals(1, httpClient.requestCount());
        HttpRequest request = httpClient.requests().getFirst();
        assertEquals("POST", request.method());
        assertEquals(URI.create("https://misskey.io/api/notes/show"), request.uri());
        assertEquals(Duration.ofSeconds(15), request.timeout().orElseThrow());
        assertEquals(Optional.of("application/json"), request.headers().firstValue("Content-Type"));
        assertEquals("{\"noteId\":\"abc123\"}", httpClient.bodies().getFirst());
    }

    @Test
    void doesNotCacheMalformedOrNullNotes() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(200, "<html>temporarily unavailable</html>"),
                response(200, "null"),
                response(200, noteJson("abc123", "Recovered"))
        );
        MisskeyClient client = client(httpClient);

        Optional<MisskeyNote> malformed = client.getNote("https://misskey.io", "abc123");
        Optional<MisskeyNote> nullNote = client.getNote("https://misskey.io", "abc123");
        Optional<MisskeyNote> recovered = client.getNote("https://misskey.io", "abc123");

        assertTrue(malformed.isEmpty());
        assertTrue(nullNote.isEmpty());
        assertTrue(recovered.isPresent());
        assertEquals("Recovered", recovered.orElseThrow().text());
        assertEquals(3, httpClient.requestCount());
    }

    @Test
    void returnsEmptyForNonSuccessfulAndBlankResponses() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(500, noteJson("abc123", "Server error")),
                response(200, "   "),
                response(200, noteJson("abc123", "Recovered"))
        );
        MisskeyClient client = client(httpClient);

        Optional<MisskeyNote> failed = client.getNote("https://misskey.io", "abc123");
        Optional<MisskeyNote> blank = client.getNote("https://misskey.io", "abc123");
        Optional<MisskeyNote> recovered = client.getNote("https://misskey.io", "abc123");

        assertTrue(failed.isEmpty());
        assertTrue(blank.isEmpty());
        assertTrue(recovered.isPresent());
        assertEquals("Recovered", recovered.orElseThrow().text());
        assertEquals(3, httpClient.requestCount());
    }

    private static MisskeyClient client(HttpClient httpClient) {
        return new MisskeyClient(
                httpClient,
                new ObjectMapper(),
                new SaucyLinkCache<>(Duration.ofMinutes(10), () -> 1_000L)
        );
    }

    private static HttpResponse<String> response(int statusCode, String body) {
        return new FakeHttpResponse<>(statusCode, body);
    }

    private static String noteJson(String id, String text) {
        return """
                {
                  "id": "%s",
                  "createdAt": "2025-01-01T00:00:00.000Z",
                  "text": "%s",
                  "visibility": "public",
                  "user": {
                    "id": "u1",
                    "name": "Alice",
                    "username": "alice",
                    "avatarUrl": "https://cdn.example/avatar.png"
                  },
                  "files": [
                    {
                      "id": "f1",
                      "type": "image/png",
                      "size": 1234,
                      "isSensitive": false,
                      "url": "https://cdn.example/image.png",
                      "thumbnailUrl": "https://cdn.example/thumb.png"
                    }
                  ]
                }
                """.formatted(id, text);
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final ArrayDeque<HttpResponse<String>> responses;
        private final List<HttpRequest> requests = new java.util.ArrayList<>();
        private final List<String> bodies = new java.util.ArrayList<>();

        @SafeVarargs
        private RecordingHttpClient(HttpResponse<String>... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        private int requestCount() {
            return requests.size();
        }

        private List<HttpRequest> requests() {
            return requests;
        }

        private List<String> bodies() {
            return bodies;
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
                throws IOException {
            requests.add(request);
            bodies.add(readBody(request));
            HttpResponse<String> response = responses.pollFirst();
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
            throw new UnsupportedOperationException("sendAsync is not used by MisskeyClient");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException("sendAsync is not used by MisskeyClient");
        }

        private static String readBody(HttpRequest request) throws IOException {
            BodyCollector collector = new BodyCollector();
            request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()).subscribe(collector);
            return collector.body();
        }
    }

    private static final class BodyCollector implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<String> result = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            output.writeBytes(bytes);
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(output.toString(StandardCharsets.UTF_8));
        }

        private String body() throws IOException {
            try {
                return result.get();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading request body", interruptedException);
            } catch (ExecutionException executionException) {
                throw new IOException("Failed to read request body", executionException);
            }
        }
    }

    private record FakeHttpResponse<T>(int statusCode, T body) implements HttpResponse<T> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://misskey.io/api/notes/show");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
