package org.camelia.studio.kiss.shot.acerola.services.saucy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class SaucySiteManager {
    private static final Logger logger = LoggerFactory.getLogger(SaucySiteManager.class);

    private final List<SaucySite> sites;
    private final Map<String, SaucySite> sitesById;
    private final SaucyLinkEmbedConfig config;

    public SaucySiteManager(List<SaucySite> sites, SaucyLinkEmbedConfig config) {
        this.sites = List.copyOf(sites);
        this.config = config;
        this.sitesById = new LinkedHashMap<>();
        for (SaucySite site : sites) {
            sitesById.put(site.id(), site);
        }
    }

    public List<SaucyMatch> match(String content) {
        List<SaucyMatch> matches = new ArrayList<>();
        int maxLinks = config.maxLinksPerMessage();

        for (SaucySite site : sites) {
            int remainingSlots = maxLinks - matches.size();
            if (remainingSlots <= 0) {
                break;
            }

            try {
                List<SaucyMatch> siteMatches = site.match(content, remainingSlots);
                int acceptedMatches = Math.min(siteMatches.size(), remainingSlots);
                matches.addAll(siteMatches.subList(0, acceptedMatches));
            } catch (RuntimeException exception) {
                logger.warn("Failed to match saucy links for site {}", site.id(), exception);
            }
        }

        return List.copyOf(matches);
    }

    public CompletableFuture<List<SaucyProcessResponse>> process(String content) {
        List<SaucyMatch> matches = match(content);
        return CompletableFuture.supplyAsync(() -> {
            List<SaucyProcessResponse> responses = new ArrayList<>();

            for (SaucyMatch match : matches) {
                SaucySite site = sitesById.get(match.siteId());
                if (site == null) {
                    logger.warn("Skipping saucy match for unknown site id {}", match.siteId());
                    continue;
                }

                try {
                    Optional<SaucyProcessResponse> response = site.process(match).join();
                    response.filter(processResponse -> !processResponse.isEmpty())
                            .ifPresent(responses::add);
                } catch (CompletionException exception) {
                    logger.warn("Failed to process saucy match {}", match.url(), exception.getCause());
                } catch (RuntimeException exception) {
                    logger.warn("Failed to process saucy match {}", match.url(), exception);
                }
            }

            return List.copyOf(responses);
        });
    }
}
