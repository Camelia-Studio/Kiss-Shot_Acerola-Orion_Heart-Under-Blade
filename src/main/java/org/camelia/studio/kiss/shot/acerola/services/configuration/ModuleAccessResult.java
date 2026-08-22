package org.camelia.studio.kiss.shot.acerola.services.configuration;

public record ModuleAccessResult(boolean allowed, String message, ModuleConfiguration configuration) {
    public static ModuleAccessResult allowed(ModuleConfiguration configuration) {
        return new ModuleAccessResult(true, null, configuration);
    }

    public static ModuleAccessResult denied(String message, ModuleConfiguration configuration) {
        return new ModuleAccessResult(false, message, configuration);
    }
}
