package org.camelia.studio.kiss.shot.acerola.services.configuration;

public record ModuleValidationResult(boolean valid, String reason) {
    public static ModuleValidationResult success() {
        return new ModuleValidationResult(true, null);
    }

    public static ModuleValidationResult invalid(String reason) {
        return new ModuleValidationResult(false, reason);
    }
}
