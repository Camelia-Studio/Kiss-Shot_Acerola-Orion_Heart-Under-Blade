package org.camelia.studio.kiss.shot.acerola.listeners.global;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyIgnoredContent;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkCache;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyLinkEmbedConfig;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMessagePartitioner;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyMessageSender;
import org.camelia.studio.kiss.shot.acerola.services.saucy.SaucyNsfwGuard;
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
import java.util.Optional;

public class SaucyLinkEmbedListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SaucyLinkEmbedListener.class);

    private final SaucyLinkEmbedConfig config;
    private final SaucySiteManager siteManager;
    private final SaucyMessageSender sender;

    public SaucyLinkEmbedListener() {
        config = SaucyLinkEmbedConfig.fromEnvironment();

        Duration cacheTtl = Duration.ofSeconds(config.cacheTtlSeconds());
        SaucyLinkCache<String> cache = new SaucyLinkCache<>(cacheTtl);
        List<SaucySite> sites = List.of(
                new FxTwitterSite(config, cache),
                new PixivSite(config, cache),
                new MisskeySite(config, cache)
        );

        siteManager = new SaucySiteManager(sites, config);
        SaucyMessagePartitioner partitioner = new SaucyMessagePartitioner(
                config.maxEmbedsPerMessage(),
                config.maxFileBytes()
        );
        SaucyNsfwGuard nsfwGuard = new SaucyNsfwGuard();
        sender = new SaucyMessageSender(config, partitioner, nsfwGuard);

        if (config.enabled()) {
            logger.info("SaucyLinkEmbed actif avec {} site(s)", sites.size());
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw();
        Optional<String> ignoreReason = ignoreReason(
                config.enabled(),
                event.isFromGuild(),
                event.getAuthor().isBot(),
                content
        );
        if (ignoreReason.isPresent()) {
            logger.debug(
                    "Ignoring saucy processing for message {} in channel {}: {}",
                    event.getMessageId(),
                    event.getChannel().getId(),
                    ignoreReason.get()
            );
            return;
        }

        siteManager.process(content)
                .thenAccept(responses -> sender.send(event.getMessage(), responses))
                .exceptionally(error -> {
                    logger.warn(
                            "Failed to process saucy links for message {} in channel {}",
                            event.getMessageId(),
                            event.getChannel().getId(),
                            error
                    );
                    return null;
                });
    }

    static Optional<String> ignoreReason(boolean enabled, boolean fromGuild, boolean authorBot, String content) {
        if (!enabled) {
            return Optional.of("saucy link embeds are disabled");
        }
        if (!fromGuild) {
            return Optional.of("message is outside a guild");
        }
        if (authorBot) {
            return Optional.of("author is a bot");
        }
        if (content == null || content.isBlank()) {
            return Optional.of("message content is blank");
        }
        if (SaucyIgnoredContent.hasIgnoredLink(content)) {
            return Optional.of("link is explicitly ignored");
        }

        return Optional.empty();
    }
}
