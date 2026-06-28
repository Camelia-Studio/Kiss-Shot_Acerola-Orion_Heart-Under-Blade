package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

interface MisskeyGateway {
    Optional<MisskeyNote> getNote(String baseUrl, String id);

    long contentLength(String url);

    byte[] download(String url, long maxBytes);
}

public class MisskeyClient implements MisskeyGateway {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SaucyLinkCache<String> cache;

    public MisskeyClient(HttpClient httpClient, ObjectMapper objectMapper, SaucyLinkCache<String> cache) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.cache = cache;
    }

    @Override
    public Optional<MisskeyNote> getNote(String baseUrl, String id) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (normalizedBaseUrl.isBlank() || id == null || id.isBlank()) {
            return Optional.empty();
        }

        String cacheKey = "misskey:%s:%s".formatted(normalizedBaseUrl, id);
        String json = cache.getIfPresent(cacheKey);
        boolean cacheHit = json != null;
        if (json == null) {
            json = fetchNoteJson(normalizedBaseUrl, id);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
        }

        Optional<MisskeyNote> note = parseUsableNote(json);
        if (note.isEmpty()) {
            if (cacheHit) {
                cache.put(cacheKey, null);
            }

            return Optional.empty();
        }

        if (!cacheHit) {
            cache.put(cacheKey, json);
        }

        return note;
    }

    @Override
    public long contentLength(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
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
    public byte[] download(String url, long maxBytes) {
        if (maxBytes <= 0) {
            return new byte[0];
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new byte[0];
            }

            try (InputStream stream = response.body()) {
                return readCapped(stream, maxBytes);
            }
        } catch (IllegalArgumentException | IOException ignored) {
            return new byte[0];
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return new byte[0];
        }
    }

    private static byte[] readCapped(InputStream stream, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = stream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return new byte[0];
            }

            output.write(buffer, 0, read);
        }

        return output.toByteArray();
    }

    private Optional<MisskeyNote> parseUsableNote(String json) {
        try {
            MisskeyNote note = objectMapper.readValue(json, MisskeyNote.class);
            if (note == null || note.id() == null || note.id().isBlank()) {
                return Optional.empty();
            }

            return Optional.of(note);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private String fetchNoteJson(String baseUrl, String id) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("noteId", id));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/notes/show"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
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

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }

        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
