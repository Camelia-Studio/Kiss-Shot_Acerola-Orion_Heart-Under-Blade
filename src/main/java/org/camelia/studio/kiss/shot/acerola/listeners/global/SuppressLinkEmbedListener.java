package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import org.camelia.studio.kiss.shot.acerola.listeners.ModuleAwareListener;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationService;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyIgnoredContent;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucySite;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucySiteManager;
import org.camelia.studio.kiss.shot.acerola.services.saucy.sites.FxTwitterSite;
import org.camelia.studio.kiss.shot.acerola.services.saucy.sites.MisskeySite;
import org.camelia.studio.kiss.shot.acerola.services.saucy.sites.PixivSite;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public class SuppressLinkEmbedListener extends ModuleAwareListener {

    private static final Logger logger = LoggerFactory.getLogger(SuppressLinkEmbedListener.class);

    private final SaucyLinkEmbedConfig saucyConfig;
    private final SaucySiteManager saucySiteManager;

    public SuppressLinkEmbedListener() {
        super(ModuleType.INTEGRATION_REMOVAL);
        saucyConfig = SaucyLinkEmbedConfig.fromEnvironment();
        saucySiteManager = buildSaucySiteManager(saucyConfig);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        Set<String> watchedChannelIds = activeConfiguration(event.getGuild())
                .map(configuration -> configuration.channels(ModuleResourcePurpose.WATCHED))
                .orElse(Set.of());
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;
        if (shouldSkipSuppression(
                event.getGuild(),
                event.getChannel().getId(),
                event.getAuthor().isBot(),
                event.getMessage().getContentRaw())) return;
        suppressIfNeeded(event.getMessage(), event.getChannel().getId());
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild()) return;
        Set<String> watchedChannelIds = activeConfiguration(event.getGuild())
                .map(configuration -> configuration.channels(ModuleResourcePurpose.WATCHED))
                .orElse(Set.of());
        if (watchedChannelIds.isEmpty()) return;
        if (!watchedChannelIds.contains(event.getChannel().getId())) return;
        if (shouldSkipSuppression(
                event.getGuild(),
                event.getChannel().getId(),
                event.getAuthor().isBot(),
                event.getMessage().getContentRaw())) return;
        suppressIfNeeded(event.getMessage(), event.getChannel().getId());
    }

    private boolean shouldSkipSuppression(Guild guild, String channelId, boolean authorBot, String content) {
        boolean ignoredSaucyContent = SaucyIgnoredContent.hasIgnoredLink(content);
        boolean linkEnrichmentActive = ModuleConfigurationService.getInstance()
                .activeConfiguration(guild, ModuleType.LINK_ENRICHMENT)
                .filter(configuration -> !configuration.channels(ModuleResourcePurpose.EXCLUDED)
                        .contains(channelId))
                .isPresent();
        boolean saucyMatches = linkEnrichmentActive
                && !ignoredSaucyContent
                && !saucySiteManager.match(content).isEmpty();
        return shouldSkipSuppression(authorBot, linkEnrichmentActive, ignoredSaucyContent, saucyMatches);
    }

    static boolean shouldSkipSuppression(
            boolean authorBot,
            boolean linkEnrichmentActive,
            boolean ignoredSaucyContent,
            boolean saucyMatches
    ) {
        return authorBot || (linkEnrichmentActive && !ignoredSaucyContent && saucyMatches);
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
