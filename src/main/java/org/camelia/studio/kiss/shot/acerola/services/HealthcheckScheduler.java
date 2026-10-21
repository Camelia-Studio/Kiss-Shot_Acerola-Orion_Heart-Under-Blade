package org.camelia.studio.kiss.shot.acerola.services;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Signale à une sonde de monitoring en mode push que le bot fonctionne. Aucun signal n'est envoyé quand le bot
 * n'est pas connecté à Discord ou que l'API est indisponible : c'est l'absence de signal que la sonde détecte.
 */
public class HealthcheckScheduler implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckScheduler.class);
    public static final Duration INTERVAL = Duration.ofSeconds(45);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @FunctionalInterface
    interface HealthcheckSender {
        int send(URI url) throws IOException, InterruptedException;
    }

    private final URI url;
    private final HealthcheckSender sender;
    private final BooleanSupplier apiAvailable;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * @return vide si {@code rawUrl} n'est pas renseignée (healthcheck désactivé)
     */
    public static Optional<HealthcheckScheduler> fromUrl(String rawUrl, BooleanSupplier apiAvailable) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        URI url;
        try {
            url = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("La variable HEALTHCHECK_URL n'est pas une URL valide", exception);
        }
        if (!"http".equals(url.getScheme()) && !"https".equals(url.getScheme())) {
            throw new IllegalStateException("La variable HEALTHCHECK_URL doit être une URL http(s)");
        }

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        HealthcheckSender sender = target -> httpClient.send(
                HttpRequest.newBuilder(target).timeout(REQUEST_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "healthcheck");
            thread.setDaemon(true);
            return thread;
        });
        return Optional.of(new HealthcheckScheduler(url, sender, apiAvailable, executor));
    }

    HealthcheckScheduler(
            URI url,
            HealthcheckSender sender,
            BooleanSupplier apiAvailable,
            ScheduledExecutorService executor
    ) {
        this.url = Objects.requireNonNull(url);
        this.sender = Objects.requireNonNull(sender);
        this.apiAvailable = Objects.requireNonNull(apiAvailable);
        this.executor = Objects.requireNonNull(executor);
    }

    public void start(JDA jda) {
        Objects.requireNonNull(jda);
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor.scheduleAtFixedRate(() -> push(jda), 0, INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    void push(JDA jda) {
        // Toute exception remontée ici annulerait les exécutions suivantes de scheduleAtFixedRate.
        try {
            if (jda.getStatus() != JDA.Status.CONNECTED) {
                logger.warn("Healthcheck non envoyé : statut Discord {}", jda.getStatus());
                return;
            }
            if (!apiAvailable.getAsBoolean()) {
                logger.warn("Healthcheck non envoyé : API indisponible");
                return;
            }
            int statusCode = sender.send(url);
            if (statusCode < 200 || statusCode >= 300) {
                logger.warn("Healthcheck refusé par la sonde (HTTP {})", statusCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            logger.warn("Impossible d'envoyer le healthcheck : {}", exception.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
