package org.camelia.studio.kiss.shot.acerola.services.configuration;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.SanctionAction;
import org.camelia.studio.kiss.shot.acerola.services.moderation.ModerationSettings;

import java.util.Arrays;
import java.util.Set;

public class ModuleConfigurationValidator {
    public ModuleValidationResult validate(Guild guild, ModuleConfiguration configuration) {
        Member self = guild.getSelfMember();
        try {
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
                case INTEGRATION_REMOVAL -> validateWatchedChannels(guild, self, configuration, true);
                case ANTI_RAID -> validateAntiRaid(guild, self, configuration);
                case AUTO_SANCTION_CHANNEL -> validateAutomaticSanction(guild, self, configuration, true, false);
                case AUTO_SANCTION_ROLE -> validateAutomaticSanction(guild, self, configuration, false, true);
                case MEMBER_AUDIT -> validateMemberAudit(guild, configuration);
            };
        } catch (IllegalArgumentException exception) {
            return ModuleValidationResult.invalid(exception.getMessage());
        }
    }

    public ModuleValidationResult validateLogChannel(Guild guild, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return ModuleValidationResult.success();
        }

        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelId);
        if (channel == null) {
            return ModuleValidationResult.invalid("Le salon de logs n'existe plus");
        }
        if (!isWritableMessageChannel(channel)) {
            return ModuleValidationResult.invalid("Le fil de logs est archivé ou verrouillé");
        }
        if (!hasMessageChannelPermissions(
                guild.getSelfMember(),
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
            boolean requireMessageManagement
    ) {
        Set<String> channelIds = configuration.channels(ModuleResourcePurpose.WATCHED);
        if (channelIds.isEmpty()) {
            return ModuleValidationResult.invalid("Au moins un salon doit être configuré");
        }
        for (String channelId : channelIds) {
            GuildChannel channel = guild.getGuildChannelById(channelId);
            if (!(channel instanceof GuildMessageChannel messageChannel)) {
                return ModuleValidationResult.invalid("Un salon configuré n'existe plus ou n'est pas textuel");
            }
            if (!self.hasPermission(messageChannel, Permission.VIEW_CHANNEL)
                    || (requireMessageManagement && !self.hasPermission(messageChannel, Permission.MESSAGE_MANAGE))) {
                return ModuleValidationResult.invalid("Permissions insuffisantes dans un salon configuré");
            }
        }
        return ModuleValidationResult.success();
    }

    private ModuleValidationResult validateAntiRaid(
            Guild guild,
            Member self,
            ModuleConfiguration configuration
    ) {
        ModuleValidationResult common = validateAutomaticSanctionCommon(guild, configuration);
        if (!common.valid()) return common;

        ModerationSettings.AntiRaid settings = ModerationSettings.antiRaid(configuration);
        if (!settings.recentAccountEnabled() && !settings.mentionSpamEnabled()) {
            return ModuleValidationResult.invalid("Au moins une règle anti-raid doit être activée");
        }
        if (settings.recentAccountEnabled()) {
            ModuleValidationResult permission = validateActionPermission(self, settings.recentAccountAction());
            if (!permission.valid()) return permission;
        }
        if (settings.mentionSpamEnabled()) {
            ModuleValidationResult permission = validateActionPermission(self, settings.mentionAction());
            if (!permission.valid()) return permission;
            if (settings.deleteMentionMessage() && !self.hasPermission(Permission.MESSAGE_MANAGE)) {
                return ModuleValidationResult.invalid("Le bot ne peut pas supprimer les messages déclencheurs");
            }
        }
        return validateProtectedRoles(guild, configuration);
    }

    private ModuleValidationResult validateMemberAudit(Guild guild, ModuleConfiguration configuration) {
        Set<String> logChannelIds = configuration.channels(ModuleResourcePurpose.LOG);
        if (logChannelIds.size() != 1) {
            return ModuleValidationResult.invalid("Un salon de logs doit être configuré pour ce module");
        }
        ModuleValidationResult logChannel = validateLogChannel(guild, logChannelIds.iterator().next());
        if (!logChannel.valid()) return logChannel;

        ModerationSettings.MemberAudit settings = ModerationSettings.memberAudit(configuration);
        if (!settings.watchNickname() && !settings.watchAvatar()) {
            return ModuleValidationResult.invalid("Au moins un élément (pseudo ou avatar) doit être surveillé");
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
        ModuleValidationResult common = validateAutomaticSanctionCommon(guild, configuration);
        if (!common.valid()) return common;

        ModerationSettings.AutomaticSanction settings = ModerationSettings.automaticSanction(configuration);
        if (settings.action().isEmpty()) {
            return ModuleValidationResult.invalid("Une action doit être choisie explicitement");
        }
        ModuleValidationResult permission = validateActionPermission(self, settings.action().get());
        if (!permission.valid()) return permission;

        if (requireChannel) {
            ModuleValidationResult channels = validateWatchedChannels(
                    guild,
                    self,
                    configuration,
                    settings.deleteMessage());
            if (!channels.valid()) return channels;
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
        return validateProtectedRoles(guild, configuration);
    }

    private ModuleValidationResult validateAutomaticSanctionCommon(
            Guild guild,
            ModuleConfiguration configuration
    ) {
        if (configuration.logChannelId() == null || configuration.logChannelId().isBlank()) {
            return ModuleValidationResult.invalid("Un salon de logs est obligatoire pour ce module");
        }
        return validateLogChannel(guild, configuration.logChannelId());
    }

    private ModuleValidationResult validateActionPermission(Member self, SanctionAction action) {
        Permission permission = switch (action) {
            case LOG_ONLY -> null;
            case TIMEOUT -> Permission.MODERATE_MEMBERS;
            case KICK -> Permission.KICK_MEMBERS;
            case BAN -> Permission.BAN_MEMBERS;
        };
        if (permission != null && !self.hasPermission(permission)) {
            return ModuleValidationResult.invalid("Le bot ne possède pas la permission requise : " + permission.getName());
        }
        return ModuleValidationResult.success();
    }

    private ModuleValidationResult validateProtectedRoles(Guild guild, ModuleConfiguration configuration) {
        for (String roleId : configuration.roles(ModuleResourcePurpose.PROTECTED)) {
            if (guild.getRoleById(roleId) == null) {
                return ModuleValidationResult.invalid("Un rôle protégé configuré n'existe plus");
            }
        }
        return ModuleValidationResult.success();
    }

    private boolean hasUsableVoiceChannel(Guild guild, Member self, Permission... permissions) {
        return guild.getVoiceChannels().stream().anyMatch(channel -> self.hasPermission(channel, permissions));
    }

    private boolean hasUsableMessageChannel(Guild guild, Member self, Permission... permissions) {
        return guild.getChannelCache().stream()
                .filter(GuildMessageChannel.class::isInstance)
                .map(GuildMessageChannel.class::cast)
                .filter(this::isWritableMessageChannel)
                .anyMatch(channel -> hasMessageChannelPermissions(self, channel, permissions));
    }

    private boolean isWritableMessageChannel(GuildMessageChannel channel) {
        return !(channel instanceof ThreadChannel thread) || (!thread.isArchived() && !thread.isLocked());
    }

    private boolean hasMessageChannelPermissions(
            Member self,
            GuildMessageChannel channel,
            Permission... permissions
    ) {
        Permission[] effectivePermissions = Arrays.stream(permissions)
                .map(permission -> channel.getType().isThread() && permission == Permission.MESSAGE_SEND
                        ? Permission.MESSAGE_SEND_IN_THREADS
                        : permission)
                .distinct()
                .toArray(Permission[]::new);
        return self.hasPermission(channel, effectivePermissions);
    }
}
