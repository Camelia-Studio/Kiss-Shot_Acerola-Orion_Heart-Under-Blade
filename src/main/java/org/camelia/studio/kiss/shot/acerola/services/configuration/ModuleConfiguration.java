package org.camelia.studio.kiss.shot.acerola.services.configuration;

import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleSetting;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record ModuleConfiguration(
        ModuleType module,
        ModuleStatus status,
        String suspensionReason,
        String logChannelId,
        Map<ModuleResourcePurpose, Set<String>> roleIds,
        Map<ModuleResourcePurpose, Set<String>> channelIds,
        Map<ModuleSetting, String> settings
) {
    public ModuleConfiguration {
        roleIds = immutableResources(roleIds);
        channelIds = immutableResources(channelIds);
        settings = Map.copyOf(settings);
    }

    public ModuleConfiguration(
            ModuleType module,
            ModuleStatus status,
            String suspensionReason,
            String logChannelId,
            Map<ModuleResourcePurpose, Set<String>> roleIds,
            Map<ModuleResourcePurpose, Set<String>> channelIds
    ) {
        this(module, status, suspensionReason, logChannelId, roleIds, channelIds, Map.of());
    }

    public Set<String> roles(ModuleResourcePurpose purpose) {
        return roleIds.getOrDefault(purpose, Set.of());
    }

    public Set<String> channels(ModuleResourcePurpose purpose) {
        return channelIds.getOrDefault(purpose, Set.of());
    }

    public ModuleConfiguration withStatus(ModuleStatus newStatus, String reason) {
        return new ModuleConfiguration(module, newStatus, reason, logChannelId, roleIds, channelIds, settings);
    }

    public ModuleConfiguration withLogChannel(String newLogChannelId) {
        return new ModuleConfiguration(module, status, suspensionReason, newLogChannelId, roleIds, channelIds, settings);
    }

    public ModuleConfiguration withRoles(ModuleResourcePurpose purpose, Set<String> ids) {
        Map<ModuleResourcePurpose, Set<String>> changed = new EnumMap<>(ModuleResourcePurpose.class);
        changed.putAll(roleIds);
        changed.put(purpose, Set.copyOf(ids));
        return new ModuleConfiguration(module, status, suspensionReason, logChannelId, changed, channelIds, settings);
    }

    public ModuleConfiguration withChannels(ModuleResourcePurpose purpose, Set<String> ids) {
        Map<ModuleResourcePurpose, Set<String>> changed = new EnumMap<>(ModuleResourcePurpose.class);
        changed.putAll(channelIds);
        changed.put(purpose, Set.copyOf(ids));
        return new ModuleConfiguration(module, status, suspensionReason, logChannelId, roleIds, changed, settings);
    }

    public ModuleConfiguration withSettings(Map<ModuleSetting, String> changedSettings) {
        Map<ModuleSetting, String> changed = new EnumMap<>(ModuleSetting.class);
        changed.putAll(settings);
        changed.putAll(changedSettings);
        return new ModuleConfiguration(module, status, suspensionReason, logChannelId, roleIds, channelIds, changed);
    }

    private static Map<ModuleResourcePurpose, Set<String>> immutableResources(
            Map<ModuleResourcePurpose, Set<String>> resources
    ) {
        Map<ModuleResourcePurpose, Set<String>> copy = new EnumMap<>(ModuleResourcePurpose.class);
        resources.forEach((purpose, ids) -> copy.put(purpose, Set.copyOf(ids)));
        return Map.copyOf(copy);
    }
}
