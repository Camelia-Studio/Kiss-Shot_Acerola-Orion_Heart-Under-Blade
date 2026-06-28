package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyIgnoredContent;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucySite;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucySiteManager;
import org.camelia.studio.kiss.shot.acerola.services.saucy.sites.FxTwitterSite;
import org.camelia.studio.kiss.shot.acerola.services.saucy.sites.MisskeySite;
import org.camelia.studio.kiss.shot.acerola.services.saucy.sites.PixivSite;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SuppressLinkEmbedListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SuppressLinkEmbedListener.class);

    private final Set<String> watchedChannelIds;
    private final SaucyLinkEmbedConfig saucyConfig;
    private final SaucySiteManager saucySiteManager;

    public SuppressLinkEmbedListener() {
        String raw = Configuration.getInstance().getDotenv().get("NO_EMBED_CHANNEL_IDS", "");
        watchedChannelIds = parseIds(raw);
        saucyConfig = SaucyLinkEmbedConfig.fromEnvironment();
        saucySiteManager = buildSaucySiteManager(saucyConfig);

        if (!watchedChannelIds.isEmpty()) {
            logger.info("SuppressLinkEmbed actif sur {} salon(s)", watchedChannelIds.size());
        }
    }

    private static Set<String> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;
        if (shouldSkipSuppression(event.getAuthor().isBot(), event.getMessage().getContentRaw())) return;
        suppressIfNeeded(event.getMessage(), event.getChannel().getId());
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild()) return;
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;
        if (shouldSkipSuppression(event.getAuthor().isBot(), event.getMessage().getContentRaw())) return;
        suppressIfNeeded(event.getMessage(), event.getChannel().getId());
    }

    private boolean shouldSkipSuppression(boolean authorBot, String content) {
        boolean ignoredSaucyContent = SaucyIgnoredContent.hasIgnoredLink(content);
        boolean saucyMatches = saucyConfig.enabled()
                && !ignoredSaucyContent
                && !saucySiteManager.match(content).isEmpty();
        return shouldSkipSuppression(authorBot, saucyConfig.enabled(), ignoredSaucyContent, saucyMatches);
    }

    static boolean shouldSkipSuppression(
            boolean authorBot,
            boolean saucyEnabled,
            boolean ignoredSaucyContent,
            boolean saucyMatches
    ) {
        return authorBot || (saucyEnabled && !ignoredSaucyContent && saucyMatches);
    }

    private void suppressIfNeeded(Message message, String channelId) {
        if (message.getEmbeds().isEmpty()) return;
        message.suppressEmbeds(true).queue(
                success -> logger.info("Intégrations supprimées dans le salon {}", channelId),
                error -> logger.error("Échec de la suppression des intégrations : {}", error.getMessage())
        );
    }

    private static SaucySiteManager buildSaucySiteManager(SaucyLinkEmbedConfig config) {
        SaucyLinkCache<String> cache = new SaucyLinkCache<>(Duration.ofSeconds(config.cacheTtlSeconds()));
        List<SaucySite> sites = List.of(
                new FxTwitterSite(config, cache),
                new PixivSite(config, cache),
                new MisskeySite(config, cache)
        );
        return new SaucySiteManager(sites, config);
    }
}
