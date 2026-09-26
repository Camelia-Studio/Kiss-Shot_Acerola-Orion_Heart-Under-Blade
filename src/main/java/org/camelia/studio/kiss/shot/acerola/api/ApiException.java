package org.camelia.studio.kiss.shot.acerola.api;

import java.util.Map;

public class ApiException extends RuntimeException {
    public static final int UNREACHABLE = 0;

    private final int status;
    private final Map<String, String> violations;

    public ApiException(int status, String message, Map<String, String> violations) {
        super(message);
        this.status = status;
        this.violations = Map.copyOf(violations);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = UNREACHABLE;
        this.violations = Map.of();
    }

    public int status() {
        return status;
    }

    public Map<String, String> violations() {
        return violations;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isInvalidRequest() {
        return status == 422;
    }

    /**
     * Message destiné à un humain : les violations d'un 422 si elles existent, sinon le message de l'API.
     */
    public String describe() {
        if (violations.isEmpty()) {
            return getMessage();
        }
        return String.join(" ; ", violations.values());
    }
}
