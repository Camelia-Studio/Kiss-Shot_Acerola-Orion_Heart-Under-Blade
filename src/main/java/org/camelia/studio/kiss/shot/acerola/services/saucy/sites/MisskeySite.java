package org.camelia.studio.kiss.shot.acerola.services.saucy.sites;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMatch;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyProcessResponse;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucySite;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MisskeySite implements SaucySite {
    private static final String ID = "misskey";
    private static final int MISSKEY_GREEN = 0x85B300;
    private static final int MAX_DESCRIPTION_LENGTH = 4_096;
    private static final String DESCRIPTION_TRUNCATION_SUFFIX = "...";

    private final MisskeyGateway gateway;
    private final Pattern urlPattern;

    MisskeySite(MisskeyGateway gateway, SaucyLinkEmbedConfig config) {
        this.gateway = gateway;
        this.urlPattern = urlPattern(config.misskeyDomains());
    }

    public MisskeySite(SaucyLinkEmbedConfig config, SaucyLinkCache<String> cache) {
        this(new MisskeyClient(HttpClient.newHttpClient(), new ObjectMapper(), cache), config);
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
        Matcher matcher = urlPattern.matcher(content);
        while (matcher.find() && matches.size() < remainingSlots) {
            String scheme = matcher.group("scheme").toLowerCase(Locale.ROOT);
            String domain = matcher.group("domain").toLowerCase(Locale.ROOT);
            Map<String, String> groups = new LinkedHashMap<>();
            groups.put("baseUrl", scheme + "://" + domain);
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
        String baseUrl = normalize(match.groups().get("baseUrl"));
        String id = normalize(match.groups().get("id"));
        if (baseUrl == null || id == null) {
            return Optional.empty();
        }

        Optional<MisskeyNote> note = gateway.getNote(baseUrl, id);
        if (note.isEmpty()) {
            return Optional.empty();
        }

        List<MisskeyFile> files = files(note.get());
        boolean sensitive = files.stream().anyMatch(MisskeyFile::isSensitive);
        List<String> imageUrls = files.stream()
                .filter(MisskeySite::isImageFile)
                .map(MisskeySite::imageUrl)
                .flatMap(Optional::stream)
                .toList();
        if (imageUrls.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new SaucyProcessResponse(
                null,
                embeds(note.get(), baseUrl, id, imageUrls),
                List.of(),
                sensitive
        ));
    }

    private static List<MessageEmbed> embeds(
            MisskeyNote note,
            String baseUrl,
            String id,
            List<String> imageUrls
    ) {
        List<MessageEmbed> embeds = new ArrayList<>();
        for (int index = 0; index < imageUrls.size(); index++) {
            embeds.add(embed(note, baseUrl, id, imageUrls.get(index), index == 0));
        }

        return List.copyOf(embeds);
    }

    private static MessageEmbed embed(
            MisskeyNote note,
            String baseUrl,
            String id,
            String imageUrl,
            boolean includeMetadata
    ) {
        String noteUrl = baseUrl + "/notes/" + id;
        EmbedBuilder builder = new EmbedBuilder()
                .setColor(MISSKEY_GREEN)
                .setUrl(noteUrl)
                .setImage(imageUrl);

        if (!includeMetadata) {
            return builder.build();
        }

        builder.setFooter("Misskey");

        String description = description(note);
        if (!description.isBlank()) {
            builder.setDescription(description);
        }

        MisskeyUser user = note.user();
        if (user != null) {
            builder.setAuthor(authorName(user), noteUrl, validHttpUrlOrNull(user.avatarUrl()));
        }

        parseTimestamp(note.createdAt()).ifPresent(builder::setTimestamp);

        return builder.build();
    }

    private static String authorName(MisskeyUser user) {
        String name = normalize(user.name());
        String username = normalize(user.username());
        if (name != null && username != null) {
            return "%s (@%s)".formatted(name, username);
        }

        if (name != null) {
            return name;
        }

        if (username != null) {
            return "@" + username;
        }

        return "Misskey";
    }

    private static Optional<Instant> parseTimestamp(String createdAt) {
        try {
            String normalized = normalize(createdAt);
            return normalized == null ? Optional.empty() : Optional.of(Instant.parse(normalized));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static String description(MisskeyNote note) {
        String description = note.text() == null ? "" : note.text();
        if (description.length() <= MAX_DESCRIPTION_LENGTH) {
            return description;
        }

        return description.substring(0, MAX_DESCRIPTION_LENGTH - DESCRIPTION_TRUNCATION_SUFFIX.length())
                + DESCRIPTION_TRUNCATION_SUFFIX;
    }

    private static List<MisskeyFile> files(MisskeyNote note) {
        if (note.files() == null) {
            return List.of();
        }

        return note.files();
    }

    private static boolean isImageFile(MisskeyFile file) {
        return file.type() != null && file.type().toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private static Optional<String> imageUrl(MisskeyFile file) {
        String url = validHttpUrlOrNull(file.url());
        if (url != null) {
            return Optional.of(url);
        }

        return Optional.ofNullable(validHttpUrlOrNull(file.thumbnailUrl()));
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

    private static Pattern urlPattern(List<String> domains) {
        List<String> configuredDomains = domains == null ? List.of() : domains;
        List<String> normalizedDomains = configuredDomains.stream()
                .map(MisskeySite::normalizeDomain)
                .filter(domain -> !domain.isBlank())
                .distinct()
                .map(Pattern::quote)
                .toList();

        if (normalizedDomains.isEmpty()) {
            return Pattern.compile("a^");
        }

        return Pattern.compile(
                "(?i:(?<scheme>https?)://(?:www\\.)?(?<domain>" + String.join("|", normalizedDomains) + "))/notes/" +
                        "(?<id>[0-9a-z]+)(?=$|[\\s<>)\\].,!?:;]|\\?)"
        );
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }

        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        }

        int slash = normalized.indexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(0, slash);
        }

        if (normalized.startsWith("www.")) {
            normalized = normalized.substring("www.".length());
        }

        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
