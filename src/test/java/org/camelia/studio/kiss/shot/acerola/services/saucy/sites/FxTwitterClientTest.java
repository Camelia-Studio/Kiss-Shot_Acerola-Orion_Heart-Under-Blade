package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxTwitterClientTest {
    @Test
    void doesNotCacheUnparseableTweetResponses() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(200, "<html>temporarily unavailable</html>"),
                response(200, tweetJson("123", "Recovered"))
        );
        FxTwitterClient client = client(httpClient);

        Optional<FxTwitterResponse> first = client.getTweet("alice", "123", null);
        Optional<FxTwitterResponse> second = client.getTweet("alice", "123", null);

        assertTrue(first.isEmpty());
        assertTrue(second.isPresent());
        assertEquals("Recovered", second.orElseThrow().tweet().text());
        assertEquals(2, httpClient.requestCount());
    }

    @Test
    void doesNotCacheResponsesWithoutTweet() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(200, "{\"tweet\":null}"),
                response(200, tweetJson("123", "Recovered"))
        );
        FxTwitterClient client = client(httpClient);

        Optional<FxTwitterResponse> first = client.getTweet("alice", "123", null);
        Optional<FxTwitterResponse> second = client.getTweet("alice", "123", null);

        assertTrue(first.isEmpty());
        assertTrue(second.isPresent());
        assertEquals("Recovered", second.orElseThrow().tweet().text());
        assertEquals(2, httpClient.requestCount());
    }

    private static FxTwitterClient client(HttpClient httpClient) {
        return new FxTwitterClient(
                httpClient,
                new ObjectMapper(),
                new SaucyLinkCache<>(Duration.ofMinutes(10), () -> 1_000L)
        );
    }

    private static HttpResponse<String> response(int statusCode, String body) {
        return new FakeHttpResponse<>(statusCode, body);
    }

    private static String tweetJson(String id, String text) {
        return """
                {
                  "tweet": {
                    "id": "%s",
                    "url": "https://twitter.com/alice/status/%s",
                    "text": "%s",
                    "created_timestamp": 1735689600,
                    "author": {
                      "id": "42",
                      "name": "Alice",
                      "screen_name": "alice",
                      "avatar_url": "https://cdn.example/avatar.png"
                    },
                    "possibly_sensitive": false,
                    "media": {
                      "photos": [],
                      "videos": []
                    }
                  }
                }
                """.formatted(id, id, text);
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final ArrayDeque<HttpResponse<String>> responses;
        private int requestCount;

        @SafeVarargs
        private RecordingHttpClient(HttpResponse<String>... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        private int requestCount() {
            return requestCount;
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
            requestCount++;
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
            throw new UnsupportedOperationException("sendAsync is not used by FxTwitterClient");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException("sendAsync is not used by FxTwitterClient");
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
            return URI.create("https://api.fxtwitter.com/alice/status/123");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
