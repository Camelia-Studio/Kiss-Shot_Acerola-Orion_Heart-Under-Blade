package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

interface FxTwitterGateway {
    Optional<FxTwitterResponse> getTweet(String user, String id, String translate);

    long contentLength(String url);

    byte[] download(String url);
}

public class FxTwitterClient implements FxTwitterGateway {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SaucyLinkCache<String> cache;

    public FxTwitterClient(HttpClient httpClient, ObjectMapper objectMapper, SaucyLinkCache<String> cache) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.cache = cache;
    }

    @Override
    public Optional<FxTwitterResponse> getTweet(String user, String id, String translate) {
        String normalizedTranslate = normalize(translate);
        String cacheKey = "fxtwitter:%s:%s:%s".formatted(user, id, normalizedTranslate == null ? "" : normalizedTranslate);
        String json = cache.get(cacheKey, () -> fetchTweetJson(user, id, normalizedTranslate));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        try {
            FxTwitterResponse response = objectMapper.readValue(json, FxTwitterResponse.class);
            return Optional.ofNullable(response);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public long contentLength(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return 0L;
            }

            return response.headers().firstValueAsLong("content-length").orElse(0L);
        } catch (IllegalArgumentException | IOException ignored) {
            return 0L;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return 0L;
        }
    }

    @Override
    public byte[] download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new byte[0];
            }

            return response.body();
        } catch (IllegalArgumentException | IOException ignored) {
            return new byte[0];
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return new byte[0];
        }
    }

    private String fetchTweetJson(String user, String id, String translate) {
        try {
            String url = "https://api.fxtwitter.com/%s/status/%s".formatted(encode(user), encode(id));
            if (translate != null) {
                url += "/" + encode(translate);
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            return response.body();
        } catch (IllegalArgumentException | IOException ignored) {
            return null;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
