package org.camelia.studio.kiss.shot.acerola.services.configuration;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.repositories.ServerConfigurationRepository;
import org.camelia.studio.kiss.shot.acerola.services.DiscordServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModuleConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(ModuleConfigurationService.class);
    private static final String SYSTEM_ACTOR = "SYSTEM_VALIDATION";
    private static ModuleConfigurationService instance;

    private final ServerConfigurationRepository repository;
    private final ModuleConfigurationValidator validator;
    private final Map<CacheKey, ModuleConfiguration> cache = new ConcurrentHashMap<>();

    public static synchronized ModuleConfigurationService getInstance() {
        if (instance == null) {
            instance = new ModuleConfigurationService(
                    new ServerConfigurationRepository(),
                    new ModuleConfigurationValidator());
        }
        return instance;
    }

    ModuleConfigurationService(
            ServerConfigurationRepository repository,
            ModuleConfigurationValidator validator
    ) {
        this.repository = repository;
        this.validator = validator;
    }

    public ModuleAccessResult checkAccess(Guild guild, ModuleType module) {
        Optional<ModuleConfiguration> resolved = resolve(guild, module);
        if (resolved.isEmpty()) {
            return ModuleAccessResult.denied(
                    "La configuration de ce serveur est temporairement indisponible.",
                    null);
        }

        ModuleConfiguration configuration = resolved.get();
        return switch (configuration.status()) {
            case ACTIVE -> ModuleAccessResult.allowed(configuration);
            case DISABLED -> ModuleAccessResult.denied(
                    "Ce module est désactivé. Un administrateur peut l'activer avec `/config`.",
                    configuration);
            case SUSPENDED -> ModuleAccessResult.denied(
                    "Ce module est suspendu : " + configuration.suspensionReason()
                            + ". Un administrateur peut corriger la configuration puis la revalider avec `/config`.",
                    configuration);
        };
    }

    public Optional<ModuleConfiguration> activeConfiguration(Guild guild, ModuleType module) {
        ModuleAccessResult result = checkAccess(guild, module);
        return result.allowed() ? Optional.of(result.configuration()) : Optional.empty();
    }

    public synchronized List<ModuleConfiguration> list(Guild guild) {
        DiscordServerService.getInstance().register(guild.getId());
        List<ModuleConfiguration> configurations = repository.findAll(guild.getId());
        List<ModuleConfiguration> resolved = new ArrayList<>();
        for (ModuleConfiguration configuration : configurations) {
            ModuleConfiguration validated = validateActive(guild, configuration);
            cache.put(new CacheKey(guild.getId(), configuration.module()), validated);
            resolved.add(validated);
        }
        resolved.sort(Comparator.comparing(configuration -> configuration.module().ordinal()));
        return List.copyOf(resolved);
    }

    public synchronized ConfigurationOperationResult activate(
            Guild guild,
            ModuleType module,
            String actorId,
            String roleId,
            String channelId
    ) {
        ModuleConfiguration current = loadFresh(guild, module).orElse(null);
        if (current == null) {
            return ConfigurationOperationResult.failure("Configuration de module introuvable.");
        }

        ModuleConfiguration candidate = applyResources(current, roleId, channelId);
        if (candidate == null) {
            return ConfigurationOperationResult.failure(
                    "Le rôle ou le salon fourni ne correspond pas à ce module.");
        }

        ModuleValidationResult validation = validator.validate(guild, candidate);
        if (!validation.valid()) {
            return ConfigurationOperationResult.failure("Activation refusée : " + validation.reason() + ".");
        }

        ModuleConfiguration active = repository.activate(guild.getId(), candidate, actorId);
        cache.put(new CacheKey(guild.getId(), module), active);
        return ConfigurationOperationResult.success("Le module est maintenant actif.");
    }

    public synchronized ConfigurationOperationResult disable(
            Guild guild,
            ModuleType module,
            String actorId
    ) {
        if (loadFresh(guild, module).isEmpty()) {
            return ConfigurationOperationResult.failure("Configuration de module introuvable.");
        }
        ModuleConfiguration disabled = repository.changeStatus(
                guild.getId(), module, ModuleStatus.DISABLED, null, actorId);
        cache.put(new CacheKey(guild.getId(), module), disabled);
        return ConfigurationOperationResult.success(
                "Le module est désactivé. Ses réglages sont conservés.");
    }

    public synchronized ConfigurationOperationResult revalidate(
            Guild guild,
            ModuleType module,
            String actorId
    ) {
        ModuleConfiguration current = loadFresh(guild, module).orElse(null);
        if (current == null) {
            return ConfigurationOperationResult.failure("Configuration de module introuvable.");
        }
        if (current.status() == ModuleStatus.DISABLED) {
            return ConfigurationOperationResult.failure(
                    "Le module est désactivé. Utilisez l'action d'activation.");
        }

        ModuleValidationResult validation = validator.validate(guild, current);
        if (!validation.valid()) {
            current = repository.changeStatus(
                    guild.getId(), module, ModuleStatus.SUSPENDED, validation.reason(), SYSTEM_ACTOR);
            cache.put(new CacheKey(guild.getId(), module), current);
            return ConfigurationOperationResult.failure("Validation échouée : " + validation.reason() + ".");
        }

        ModuleConfiguration active = repository.changeStatus(
                guild.getId(), module, ModuleStatus.ACTIVE, null, actorId);
        cache.put(new CacheKey(guild.getId(), module), active);
        return ConfigurationOperationResult.success("Le module est valide et actif.");
    }

    public synchronized ConfigurationOperationResult setLogChannel(
            Guild guild,
            String channelId,
            String actorId
    ) {
        ModuleValidationResult validation = validator.validateLogChannel(guild, channelId);
        if (!validation.valid()) {
            return ConfigurationOperationResult.failure(validation.reason() + ".");
        }

        DiscordServerService.getInstance().register(guild.getId());
        repository.setLogChannel(guild.getId(), channelId, actorId);
        cache.keySet().removeIf(key -> key.guildId().equals(guild.getId()));
        list(guild);
        return ConfigurationOperationResult.success(channelId == null
                ? "Le salon de logs a été supprimé. Les modules qui en dépendent ont été suspendus."
                : "Le salon de logs a été mis à jour.");
    }

    public synchronized void evictGuild(String guildId) {
        cache.keySet().removeIf(key -> key.guildId().equals(guildId));
    }

    private synchronized Optional<ModuleConfiguration> resolve(Guild guild, ModuleType module) {
        CacheKey key = new CacheKey(guild.getId(), module);
        ModuleConfiguration configuration = cache.get(key);
        try {
            if (configuration == null) {
                configuration = loadFresh(guild, module).orElse(null);
            }
            if (configuration == null) {
                return Optional.empty();
            }

            ModuleConfiguration validated = validateActive(guild, configuration);
            cache.put(key, validated);
            return Optional.of(validated);
        } catch (RuntimeException exception) {
            logger.error(
                    "Impossible de résoudre la configuration du module {} pour le serveur {}",
                    module,
                    guild.getId(),
                    exception);
            return Optional.empty();
        }
    }

    private Optional<ModuleConfiguration> loadFresh(Guild guild, ModuleType module) {
        DiscordServerService.getInstance().register(guild.getId());
        Optional<ModuleConfiguration> configuration = repository.find(guild.getId(), module);
        configuration.ifPresent(value -> cache.put(new CacheKey(guild.getId(), module), value));
        return configuration;
    }

    private ModuleConfiguration validateActive(Guild guild, ModuleConfiguration configuration) {
        if (configuration.status() != ModuleStatus.ACTIVE) {
            return configuration;
        }

        ModuleValidationResult validation = validator.validate(guild, configuration);
        if (validation.valid()) {
            return configuration;
        }

        logger.warn(
                "Suspension du module {} pour le serveur {} : {}",
                configuration.module(),
                guild.getId(),
                validation.reason());
        return repository.changeStatus(
                guild.getId(),
                configuration.module(),
                ModuleStatus.SUSPENDED,
                validation.reason(),
                SYSTEM_ACTOR);
    }

    private ModuleConfiguration applyResources(
            ModuleConfiguration current,
            String roleId,
            String channelId
    ) {
        ModuleConfiguration candidate = current;

        if (roleId != null) {
            candidate = switch (current.module()) {
                case AUTO_ROLE -> candidate.withRoles(ModuleResourcePurpose.TARGET, Set.of(roleId));
                case AUTO_SANCTION_ROLE -> candidate.withRoles(ModuleResourcePurpose.WATCHED, Set.of(roleId));
                case ANTI_RAID, AUTO_SANCTION_CHANNEL ->
                        candidate.withRoles(ModuleResourcePurpose.PROTECTED, Set.of(roleId));
                default -> null;
            };
            if (candidate == null) {
                return null;
            }
        }

        if (channelId != null) {
            candidate = switch (current.module()) {
                case INTEGRATION_REMOVAL, AUTO_SANCTION_CHANNEL ->
                        candidate.withChannels(ModuleResourcePurpose.WATCHED, Set.of(channelId));
                case LINK_ENRICHMENT ->
                        candidate.withChannels(ModuleResourcePurpose.EXCLUDED, Set.of(channelId));
                default -> null;
            };
        }
        return candidate;
    }

    private record CacheKey(String guildId, ModuleType module) {
    }
}
