package org.camelia.studio.kiss.shot.acerola.services.configuration;

import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record ModuleConfigurationSettings(
        String logChannelId,
        Map<ModuleResourcePurpose, Set<String>> roleIds,
        Map<ModuleResourcePurpose, Set<String>> channelIds
) {
    public ModuleConfigurationSettings {
        roleIds = immutableResources(roleIds);
        channelIds = immutableResources(channelIds);
    }

    public static ModuleConfigurationSettings empty() {
        return new ModuleConfigurationSettings(null, Map.of(), Map.of());
    }

    public ModuleConfiguration applyTo(ModuleConfiguration current) {
        ModuleConfiguration candidate = logChannelId == null
                ? current
                : current.withLogChannel(logChannelId);

        for (Map.Entry<ModuleResourcePurpose, Set<String>> entry : roleIds.entrySet()) {
            candidate = candidate.withRoles(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<ModuleResourcePurpose, Set<String>> entry : channelIds.entrySet()) {
            candidate = candidate.withChannels(entry.getKey(), entry.getValue());
        }
        return candidate;
    }

    private static Map<ModuleResourcePurpose, Set<String>> immutableResources(
            Map<ModuleResourcePurpose, Set<String>> resources
    ) {
        Map<ModuleResourcePurpose, Set<String>> copy = new EnumMap<>(ModuleResourcePurpose.class);
        resources.forEach((purpose, ids) -> copy.put(purpose, Set.copyOf(ids)));
        return Map.copyOf(copy);
    }
}
