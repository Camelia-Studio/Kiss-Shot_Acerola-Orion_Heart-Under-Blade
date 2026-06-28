package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

interface PixivGateway {
    boolean hasSessionCookie();

    Optional<PixivIllustrationResponse> illustrationDetails(String id);

    Optional<PixivPagesResponse> illustrationPages(String id);

    long contentLength(String url);

    Optional<byte[]> download(String url, long maxBytes);
}

public class PixivClient implements PixivGateway {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String PIXIV_REFERER = "https://www.pixiv.net/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SaucyLinkCache<String> cache;
    private final String sessionCookie;

    public PixivClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            SaucyLinkCache<String> cache,
            String sessionCookie
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.sessionCookie = normalize(sessionCookie);
    }

    @Override
    public boolean hasSessionCookie() {
        return sessionCookie != null;
    }

    @Override
    public Optional<PixivIllustrationResponse> illustrationDetails(String id) {
        String normalizedId = normalize(id);
        if (normalizedId == null || !hasSessionCookie()) {
            return Optional.empty();
        }

        String cacheKey = "pixiv:details:" + normalizedId;
        String json = cache.getIfPresent(cacheKey);
        boolean cacheHit = json != null;
        if (json == null) {
            json = fetchJson("https://www.pixiv.net/ajax/illust/" + encode(normalizedId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
        }

        Optional<PixivIllustrationResponse> response = parseUsableDetails(json);
        if (response.isEmpty()) {
            if (cacheHit) {
                cache.put(cacheKey, null);
            }

            return Optional.empty();
        }

        if (!cacheHit) {
            cache.put(cacheKey, json);
        }

        return response;
    }

    @Override
    public Optional<PixivPagesResponse> illustrationPages(String id) {
        String normalizedId = normalize(id);
        if (normalizedId == null || !hasSessionCookie()) {
            return Optional.empty();
        }

        String cacheKey = "pixiv:pages:" + normalizedId;
        String json = cache.getIfPresent(cacheKey);
        boolean cacheHit = json != null;
        if (json == null) {
            json = fetchJson("https://www.pixiv.net/ajax/illust/" + encode(normalizedId) + "/pages");
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
        }

        Optional<PixivPagesResponse> response = parseUsablePages(json);
        if (response.isEmpty()) {
            if (cacheHit) {
                cache.put(cacheKey, null);
            }

            return Optional.empty();
        }

        if (!cacheHit) {
            cache.put(cacheKey, json);
        }

        return response;
    }

    @Override
    public long contentLength(String url) {
        if (!hasSessionCookie()) {
            return 0L;
        }

        try {
            HttpRequest request = request(url)
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
    public Optional<byte[]> download(String url, long maxBytes) {
        if (maxBytes <= 0 || !hasSessionCookie()) {
            return Optional.empty();
        }

        try {
            HttpRequest request = request(url)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            try (InputStream stream = response.body()) {
                return readCapped(stream, maxBytes);
            }
        } catch (IllegalArgumentException | IOException ignored) {
            return Optional.empty();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<PixivIllustrationResponse> parseUsableDetails(String json) {
        try {
            PixivIllustrationResponse response = objectMapper.readValue(json, PixivIllustrationResponse.class);
            if (response == null || response.error() || response.body() == null) {
                return Optional.empty();
            }

            return Optional.of(response);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<PixivPagesResponse> parseUsablePages(String json) {
        try {
            PixivPagesResponse response = objectMapper.readValue(json, PixivPagesResponse.class);
            if (response == null || response.error() || response.body() == null) {
                return Optional.empty();
            }

            return Optional.of(response);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private String fetchJson(String url) {
        try {
            HttpRequest request = request(url)
                    .GET()
                    .build();
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

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Cookie", "PHPSESSID=" + sessionCookie)
                .header("User-Agent", USER_AGENT)
                .header("Referer", PIXIV_REFERER);
    }

    private static Optional<byte[]> readCapped(InputStream stream, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = stream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return Optional.empty();
            }

            output.write(buffer, 0, read);
        }

        return Optional.of(output.toByteArray());
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
