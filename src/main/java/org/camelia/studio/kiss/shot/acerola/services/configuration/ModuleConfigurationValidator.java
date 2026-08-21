package org.camelia.studio.kiss.shot.acerola.services.configuration;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;

import java.util.Set;

public class ModuleConfigurationValidator {
    public ModuleValidationResult validate(Guild guild, ModuleConfiguration configuration) {
        Member self = guild.getSelfMember();

        return switch (configuration.module()) {
            case WARNINGS -> ModuleValidationResult.success();
            case AUTO_ROLE -> validateAutoRole(guild, self, configuration);
            case MUSIC -> hasUsableVoiceChannel(guild, self, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)
                    ? ModuleValidationResult.success()
                    : ModuleValidationResult.invalid("Aucun salon vocal accessible avec les permissions connexion et parole");
            case VOICE_RECORDING -> hasUsableVoiceChannel(guild, self, Permission.VOICE_CONNECT)
                    ? ModuleValidationResult.success()
                    : ModuleValidationResult.invalid("Aucun salon vocal accessible avec la permission connexion");
            case BOT_MESSAGES -> hasUsableMessageChannel(
                    guild,
                    self,
                    Permission.VIEW_CHANNEL,
                    Permission.MESSAGE_SEND,
                    Permission.MESSAGE_HISTORY)
                    ? ModuleValidationResult.success()
                    : ModuleValidationResult.invalid("Aucun salon textuel accessible pour envoyer et modifier des messages");
            case LINK_ENRICHMENT -> hasUsableMessageChannel(
                    guild,
                    self,
                    Permission.VIEW_CHANNEL,
                    Permission.MESSAGE_SEND,
                    Permission.MESSAGE_EMBED_LINKS,
                    Permission.MESSAGE_ATTACH_FILES)
                    ? ModuleValidationResult.success()
                    : ModuleValidationResult.invalid("Aucun salon textuel accessible pour publier les enrichissements");
            case INTEGRATION_REMOVAL -> validateWatchedChannels(guild, self, configuration, false);
            case ANTI_RAID -> validateAutomaticSanction(guild, self, configuration, false, false);
            case AUTO_SANCTION_CHANNEL -> validateAutomaticSanction(guild, self, configuration, true, false);
            case AUTO_SANCTION_ROLE -> validateAutomaticSanction(guild, self, configuration, false, true);
        };
    }

    public ModuleValidationResult validateLogChannel(Guild guild, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return ModuleValidationResult.success();
        }

        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelId);
        if (channel == null) {
            return ModuleValidationResult.invalid("Le salon de logs n'existe plus");
        }
        if (!guild.getSelfMember().hasPermission(
                channel,
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS)) {
            return ModuleValidationResult.invalid("Permissions insuffisantes dans le salon de logs");
        }
        return ModuleValidationResult.success();
    }

    private ModuleValidationResult validateAutoRole(
            Guild guild,
            Member self,
            ModuleConfiguration configuration
    ) {
        Set<String> targetRoleIds = configuration.roles(ModuleResourcePurpose.TARGET);
        if (targetRoleIds.size() != 1) {
            return ModuleValidationResult.invalid("Un rôle automatique doit être configuré");
        }

        Role role = guild.getRoleById(targetRoleIds.iterator().next());
        if (role == null) {
            return ModuleValidationResult.invalid("Le rôle automatique configuré n'existe plus");
        }
        if (!self.hasPermission(Permission.MANAGE_ROLES) || !self.canInteract(role)) {
            return ModuleValidationResult.invalid("Le bot ne peut pas attribuer le rôle automatique configuré");
        }
        return ModuleValidationResult.success();
    }

    private ModuleValidationResult validateWatchedChannels(
            Guild guild,
            Member self,
            ModuleConfiguration configuration,
            boolean requireBanPermission
    ) {
        Set<String> channelIds = configuration.channels(ModuleResourcePurpose.WATCHED);
        if (channelIds.isEmpty()) {
            return ModuleValidationResult.invalid("Au moins un salon doit être configuré");
        }
        if (requireBanPermission && !self.hasPermission(Permission.BAN_MEMBERS)) {
            return ModuleValidationResult.invalid("Le bot n'a pas la permission de bannir des membres");
        }

        for (String channelId : channelIds) {
            GuildChannel channel = guild.getGuildChannelById(channelId);
            if (!(channel instanceof GuildMessageChannel messageChannel)) {
                return ModuleValidationResult.invalid("Un salon configuré n'existe plus ou n'est pas textuel");
            }
            if (!self.hasPermission(messageChannel, Permission.VIEW_CHANNEL, Permission.MESSAGE_MANAGE)) {
                return ModuleValidationResult.invalid("Permissions insuffisantes dans un salon configuré");
            }
        }
        return ModuleValidationResult.success();
    }

    private ModuleValidationResult validateAutomaticSanction(
            Guild guild,
            Member self,
            ModuleConfiguration configuration,
            boolean requireChannel,
            boolean requireRole
    ) {
        ModuleValidationResult logValidation = validateLogChannel(guild, configuration.logChannelId());
        if (configuration.logChannelId() == null || configuration.logChannelId().isBlank()) {
            return ModuleValidationResult.invalid("Un salon de logs est obligatoire pour ce module");
        }
        if (!logValidation.valid()) {
            return logValidation;
        }
        if (!self.hasPermission(Permission.BAN_MEMBERS)) {
            return ModuleValidationResult.invalid("Le bot n'a pas la permission de bannir des membres");
        }

        if (requireChannel) {
            return validateWatchedChannels(guild, self, configuration, true);
        }
        if (requireRole) {
            Set<String> roleIds = configuration.roles(ModuleResourcePurpose.WATCHED);
            if (roleIds.isEmpty()) {
                return ModuleValidationResult.invalid("Au moins un rôle déclencheur doit être configuré");
            }
            for (String roleId : roleIds) {
                Role role = guild.getRoleById(roleId);
                if (role == null) {
                    return ModuleValidationResult.invalid("Un rôle déclencheur configuré n'existe plus");
                }
            }
        }
        return ModuleValidationResult.success();
    }

    private boolean hasUsableVoiceChannel(Guild guild, Member self, Permission... permissions) {
        return guild.getVoiceChannels().stream().anyMatch(channel -> self.hasPermission(channel, permissions));
    }

    private boolean hasUsableMessageChannel(Guild guild, Member self, Permission... permissions) {
        return guild.getTextChannels().stream().anyMatch(channel -> self.hasPermission(channel, permissions));
    }
}
