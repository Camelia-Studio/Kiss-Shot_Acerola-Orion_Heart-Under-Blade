package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyFileAttachment;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMatch;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyProcessResponse;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucySite;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PixivSite implements SaucySite {
    private static final String ID = "pixiv";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i:https?://(?:www\\.)?pixiv\\.net)/(?:[^\\s<>)\\].,!?:;]+/)*artworks/" +
                    "(?<id>\\d+)/?(?=$|[\\s<>)\\].,!?:;]|\\?)"
    );

    private final PixivGateway gateway;
    private final SaucyLinkEmbedConfig config;

    PixivSite(PixivGateway gateway, SaucyLinkEmbedConfig config) {
        this.gateway = gateway;
        this.config = config;
    }

    public PixivSite(SaucyLinkEmbedConfig config, SaucyLinkCache<String> cache) {
        this(new PixivClient(HttpClient.newHttpClient(), new ObjectMapper(), cache, config.pixivSessionCookie()), config);
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
            groups.put("id", matcher.group("id"));
            matches.add(new SaucyMatch(ID, matcher.group(), groups));
        }

        return matches;
    }

    @Override
    public CompletableFuture<Optional<SaucyProcessResponse>> process(SaucyMatch match) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return processSynchronously(match);
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        });
    }

    private Optional<SaucyProcessResponse> processSynchronously(SaucyMatch match) {
        if (!gateway.hasSessionCookie()) {
            return Optional.empty();
        }

        String id = normalize(match.groups().get("id"));
        if (id == null) {
            return Optional.empty();
        }

        Optional<PixivIllustrationResponse> detailsResponse = gateway.illustrationDetails(id);
        if (detailsResponse.isEmpty() || detailsResponse.get().error() || detailsResponse.get().body() == null) {
            return Optional.empty();
        }

        PixivIllustration illustration = detailsResponse.get().body();
        if (illustration.illustType() == 2) {
            return Optional.empty();
        }

        int pageCount = Math.max(illustration.pageCount(), 1);
        List<ImageCandidates> images = pageCount <= 1
                ? singlePageCandidates(id, illustration.urls())
                : multiPageCandidates(id, pageCount);

        int limit = Math.max(1, config.pixivImageLimit());
        List<SaucyFileAttachment> files = new ArrayList<>();
        for (ImageCandidates image : images.stream().limit(limit).toList()) {
            attachment(image).ifPresent(files::add);
        }

        if (files.isEmpty()) {
            return Optional.empty();
        }

        String text = pageCount > limit ? "This is part of a %d image set.".formatted(pageCount) : null;
        return Optional.of(new SaucyProcessResponse(
                text,
                List.of(),
                files,
                illustration.xRestrict() > 0
        ));
    }

    private List<ImageCandidates> multiPageCandidates(String id, int pageCount) {
        Optional<PixivPagesResponse> pagesResponse = gateway.illustrationPages(id);
        if (pagesResponse.isEmpty() || pagesResponse.get().error() || pagesResponse.get().body() == null) {
            return List.of();
        }

        return pagesResponse.get().body().stream()
                .limit(pageCount)
                .map(page -> new ImageCandidates(id, page.urls() == null ? List.of() : candidateUrls(
                        page.urls().original(),
                        page.urls().regular(),
                        page.urls().small(),
                        page.urls().thumbMini()
                )))
                .toList();
    }

    private static List<ImageCandidates> singlePageCandidates(String id, PixivIllustrationUrls urls) {
        if (urls == null) {
            return List.of();
        }

        return List.of(new ImageCandidates(id, candidateUrls(
                urls.original(),
                urls.regular(),
                urls.small(),
                urls.thumb(),
                urls.mini()
        )));
    }

    private Optional<SaucyFileAttachment> attachment(ImageCandidates image) {
        for (String candidate : image.urls()) {
            String url = validHttpUrlOrNull(candidate);
            if (url == null) {
                continue;
            }

            long contentLength = gateway.contentLength(url);
            if (contentLength > config.maxFileBytes()) {
                continue;
            }

            Optional<byte[]> bytes = gateway.download(url, config.maxFileBytes());
            if (bytes.isEmpty() || bytes.get().length == 0 || bytes.get().length > config.maxFileBytes()) {
                continue;
            }

            return Optional.of(new SaucyFileAttachment(
                    fileName(url, image.id()),
                    bytes.get(),
                    contentType(url)
            ));
        }

        return Optional.empty();
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

        return "pixiv-" + id;
    }

    private static String contentType(String url) {
        String path = "";
        try {
            path = Optional.ofNullable(new URI(url).getPath()).orElse("");
        } catch (IllegalArgumentException | URISyntaxException ignored) {
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".gif")) {
            return "image/gif";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        if (normalized.endsWith(".avif")) {
            return "image/avif";
        }

        return "application/octet-stream";
    }

    private static String validHttpUrlOrNull(String url) {
        return isHttpUrl(url) ? url : null;
    }

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static List<String> candidateUrls(String... urls) {
        return java.util.Arrays.asList(urls);
    }

    private record ImageCandidates(String id, List<String> urls) {
    }
}
