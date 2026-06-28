package org.camelia.studio.kiss.shot.acerola.services.saucy;

import java.util.regex.Pattern;

public final class SaucyIgnoredContent {
    private static final Pattern IGNORED_LINK = Pattern.compile("(<|\\|\\|)https?://[^\\s>]+(>|\\|\\|)", Pattern.CASE_INSENSITIVE);

    private SaucyIgnoredContent() {
    }

    public static boolean hasIgnoredLink(String content) {
        return content != null && IGNORED_LINK.matcher(content).find();
    }
}
