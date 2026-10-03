package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Faux serveur de l'API Kiss-Shot : renvoie des réponses préparées et enregistre les requêtes reçues.
 */
public final class FakeApiServer implements AutoCloseable {
    public static final String TOKEN = "test-token";

    public record Request(String method, String path, String authorization, String body) {
    }

    private record Response(int status, String body, String location) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Response> responses = new ConcurrentHashMap<>();

    public FakeApiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    public ApiClient client() {
        return new ApiClient(HttpClient.newHttpClient(), new ObjectMapper(), baseUrl(), TOKEN);
    }

    /**
     * Prépare la réponse à {@code method path}, le chemin étant relatif à {@code /api} et query comprise.
     */
    public void respond(String method, String path, int status, String body) {
        responses.put(method + " " + path, new Response(status, body, null));
    }

    public void redirect(String method, String path, int status, String location) {
        responses.put(method + " " + path, new Response(status, "", location));
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public List<Request> requests(String method) {
        return requests.stream().filter(request -> request.method().equals(method)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath().substring("/api".length());
        if (exchange.getRequestURI().getRawQuery() != null) {
            path += "?" + exchange.getRequestURI().getRawQuery();
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(
                exchange.getRequestMethod(),
                path,
                exchange.getRequestHeaders().getFirst("Authorization"),
                body));

        Response response = responses.getOrDefault(
                exchange.getRequestMethod() + " " + path,
                new Response(404, "{\"error\":{\"status\":404,\"message\":\"Ressource introuvable.\"}}", null));
        byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (response.location() != null) {
            exchange.getResponseHeaders().add("Location", response.location());
        }
        exchange.sendResponseHeaders(response.status(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
