package org.camelia.studio.kiss.shot.acerola.services.saucy;

public class SaucyNsfwGuard {

    public boolean canPost(boolean sensitive, boolean channelNsfw) {
        return !sensitive || channelNsfw;
    }
}
