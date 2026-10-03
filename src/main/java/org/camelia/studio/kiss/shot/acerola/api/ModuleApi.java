package org.camelia.studio.kiss.shot.acerola.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfigurationSettings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration des modules d'un serveur (routes {@code /servers/{id}/modules}).
 */
public class ModuleApi {
    private static final int MAX_SUSPENSION_REASON_LENGTH = 255;

    private final ApiClient client;

    public ModuleApi(ApiClient client) {
        this.client = client;
    }

    public List<ModuleConfiguration> findAll(String discordId) {
        List<ModuleConfiguration> configurations = new ArrayList<>();
        client.get(modulesPath(discordId)).forEach(node -> configurations.add(toConfiguration(node)));
        return configurations;
    }

    public Optional<ModuleConfiguration> find(String discordId, ModuleType module) {
        return client.getIfExists(modulePath(discordId, module)).map(ModuleApi::toConfiguration);
    }

    /**
     * Modification partielle : seuls le salon de logs, les rôles, salons et réglages présents dans
     * {@code changes} sont envoyés. {@code status} à {@code null} laisse le statut inchangé.
     */
    public ModuleConfiguration configure(
            String discordId,
            ModuleType module,
            ModuleConfigurationSettings changes,
            ModuleStatus status,
            String suspensionReason,
            String actorId
    ) {
        Map<String, Object> body = statusBody(actorId, status, suspensionReason);
        if (changes.logChannelId() != null) {
            body.put("logChannelId", changes.logChannelId());
        }
        if (!changes.roleIds().isEmpty()) {
            body.put("roles", changes.roleIds());
        }
        if (!changes.channelIds().isEmpty()) {
            body.put("channels", changes.channelIds());
        }
        if (!changes.settings().isEmpty()) {
            body.put("settings", changes.settings());
        }
        return patch(discordId, module, body);
    }

    public ModuleConfiguration changeStatus(
            String discordId,
            ModuleType module,
            ModuleStatus status,
            String suspensionReason,
            String actorId
    ) {
        return patch(discordId, module, statusBody(actorId, status, suspensionReason));
    }

    private ModuleConfiguration patch(String discordId, ModuleType module, Map<String, Object> body) {
        return toConfiguration(client.patch(modulePath(discordId, module), body));
    }

    private static Map<String, Object> statusBody(String actorId, ModuleStatus status, String suspensionReason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actorId", actorId);
        if (status != null) {
            body.put("status", status.name());
            if (status == ModuleStatus.SUSPENDED) {
                body.put("suspensionReason", truncate(suspensionReason));
            }
        }
        return body;
    }

    private static String truncate(String reason) {
        return reason == null || reason.length() <= MAX_SUSPENSION_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_SUSPENSION_REASON_LENGTH);
    }

    private static String modulesPath(String discordId) {
        return "/servers/" + discordId + "/modules";
    }

    private static String modulePath(String discordId, ModuleType module) {
        return modulesPath(discordId) + "/" + module.name();
    }

    private static ModuleConfiguration toConfiguration(JsonNode node) {
        Map<ModuleSetting, String> settings = new EnumMap<>(ModuleSetting.class);
        node.path("settings").properties().forEach(entry ->
                settings.put(ModuleSetting.valueOf(entry.getKey()), entry.getValue().asText()));

        return new ModuleConfiguration(
                ModuleType.valueOf(node.path("module").asText()),
                ModuleStatus.valueOf(node.path("status").asText()),
                ApiJson.nullableText(node.path("suspensionReason")),
                ApiJson.nullableText(node.path("logChannelId")),
                resources(node.path("roles")),
                resources(node.path("channels")),
                settings);
    }

    private static Map<ModuleResourcePurpose, Set<String>> resources(JsonNode node) {
        Map<ModuleResourcePurpose, Set<String>> resources = new EnumMap<>(ModuleResourcePurpose.class);
        node.properties().forEach(entry -> {
            Set<String> ids = new LinkedHashSet<>();
            entry.getValue().forEach(id -> ids.add(id.asText()));
            resources.put(ModuleResourcePurpose.valueOf(entry.getKey()), ids);
        });
        return resources;
    }
}
