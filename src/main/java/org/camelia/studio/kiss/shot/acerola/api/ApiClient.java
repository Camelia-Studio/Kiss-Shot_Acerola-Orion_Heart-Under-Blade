package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.camelia.studio.kiss.shot.acerola.utils.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Transport HTTP/JSON vers l'API Kiss-Shot : authentification, sérialisation et traduction des erreurs.
 */
public class ApiClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static ApiClient instance;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            Dotenv dotenv = Configuration.getInstance().getDotenv();
            instance = new ApiClient(
                    HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                    new ObjectMapper(),
                    required(dotenv, "API_BASE_URL"),
                    required(dotenv, "API_BOT_TOKEN"));
        }
        return instance;
    }

    public ApiClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String token) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    public JsonNode get(String path) {
        return send("GET", path, null);
    }

    /**
     * Comme {@link #get(String)}, mais un {@code 404} donne un résultat vide.
     */
    public Optional<JsonNode> getIfExists(String path) {
        try {
            return Optional.of(get(path));
        } catch (ApiException exception) {
            if (exception.isNotFound()) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    public JsonNode put(String path, Map<String, ?> body) {
        return send("PUT", path, body);
    }

    public JsonNode post(String path, Map<String, ?> body) {
        return send("POST", path, body);
    }

    public JsonNode patch(String path, Map<String, ?> body) {
        return send("PATCH", path, body);
    }

    private JsonNode send(String method, String path, Map<String, ?> body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(serialize(body)));
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new ApiException("API injoignable : " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException("Appel de l'API interrompu", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw toException(response);
        }
        return parse(response.body());
    }

    private String serialize(Map<String, ?> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Corps de requête non sérialisable", exception);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new ApiException("Réponse de l'API illisible", exception);
        }
    }

    private ApiException toException(HttpResponse<String> response) {
        String message = "Erreur " + response.statusCode();
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            // Le client ne suit pas les redirections : un POST/PUT/PATCH redirigé serait rejoué en GET.
            message = "Redirection " + response.statusCode() + " vers "
                    + response.headers().firstValue("Location").orElse("?")
                    + " : vérifier API_BASE_URL (schéma http/https, chemin)";
        }
        Map<String, String> violations = new LinkedHashMap<>();
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            message = error.path("message").asText(message);
            error.path("violations").properties().forEach(entry ->
                    violations.put(entry.getKey(), entry.getValue().asText()));
        } catch (IOException ignored) {
            // Corps non JSON (proxy, page d'erreur) : le code HTTP suffit.
        }
        return new ApiException(response.statusCode(), message, violations);
    }

    private static String required(Dotenv dotenv, String name) {
        String value = dotenv.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("La variable " + name + " est obligatoire");
        }
        return value.trim();
    }
}
