package org.camelia.studio.kiss.shot.acerola.services.saucy;

import java.util.Map;

public record SaucyMatch(String siteId, String url, Map<String, String> groups) {
    public SaucyMatch {
        groups = Map.copyOf(groups);
    }
}
