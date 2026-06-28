package org.camelia.studio.kiss.shot.acerola.services.saucy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SaucySite {
    String id();

    List<SaucyMatch> match(String content, int remainingSlots);

    CompletableFuture<Optional<SaucyProcessResponse>> process(SaucyMatch match);
}
