package org.camelia.studio.kiss.shot.acerola.services.configuration;

public record ConfigurationOperationResult(boolean success, String message) {
    public static ConfigurationOperationResult success(String message) {
        return new ConfigurationOperationResult(true, message);
    }

    public static ConfigurationOperationResult failure(String message) {
        return new ConfigurationOperationResult(false, message);
    }
}
