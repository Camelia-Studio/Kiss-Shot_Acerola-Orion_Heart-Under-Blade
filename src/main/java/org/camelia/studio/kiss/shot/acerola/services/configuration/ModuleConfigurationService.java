package org.camelia.studio.kiss.shot.acerola.services.configuration;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.kiss.shot.acerola.api.ApiClient;
import org.camelia.studio.kiss.shot.acerola.api.ApiException;
import org.camelia.studio.kiss.shot.acerola.api.ModuleApi;
import org.camelia.studio.kiss.shot.acerola.api.ServerApi;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ModuleConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(ModuleConfigurationService.class);
    private static final String SYSTEM_ACTOR = "SYSTEM_VALIDATION";
    /**
     * Durée courte : une modification faite depuis le backoffice doit rester visible rapidement.
     */
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static ModuleConfigurationService instance;

    private final ModuleApi moduleApi;
    private final ServerApi serverApi;
    private final ModuleConfigurationValidator validator;
    private final Clock clock;
    private final Map<CacheKey, CachedConfiguration> cache = new ConcurrentHashMap<>();

    public static synchronized ModuleConfigurationService getInstance() {
        if (instance == null) {
            ApiClient client = ApiClient.getInstance();
            instance = new ModuleConfigurationService(
                    new ModuleApi(client),
                    new ServerApi(client),
                    new ModuleConfigurationValidator(),
                    Clock.systemUTC());
        }
        return instance;
    }

    ModuleConfigurationService(
            ModuleApi moduleApi,
            ServerApi serverApi,
            ModuleConfigurationValidator validator,
            Clock clock
    ) {
        this.moduleApi = moduleApi;
        this.serverApi = serverApi;
        this.validator = validator;
        this.clock = clock;
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
        List<ModuleConfiguration> configurations = moduleApi.findAll(guild.getId());
        List<ModuleConfiguration> resolved = new ArrayList<>();
        for (ModuleConfiguration configuration : configurations) {
            ModuleConfiguration validated = validateActive(guild, configuration);
            putInCache(guild.getId(), validated);
            resolved.add(validated);
        }
        resolved.sort(Comparator.comparing(configuration -> configuration.module().ordinal()));
        return List.copyOf(resolved);
    }

    public synchronized Optional<ModuleConfiguration> find(Guild guild, ModuleType module) {
        return loadFresh(guild, module);
    }

    public synchronized ConfigurationOperationResult activate(
            Guild guild,
            ModuleType module,
            String actorId
    ) {
        return activate(guild, module, actorId, ModuleConfigurationSettings.empty());
    }

    public synchronized ConfigurationOperationResult activate(
            Guild guild,
            ModuleType module,
            String actorId,
            ModuleConfigurationSettings settings
    ) {
        ModuleConfiguration current = loadFresh(guild, module).orElse(null);
        if (current == null) {
            return ConfigurationOperationResult.failure("Configuration de module introuvable.");
        }

        ModuleConfiguration candidate = settings.applyTo(current);

        ModuleValidationResult validation = validator.validate(guild, candidate);
        if (!validation.valid()) {
            return ConfigurationOperationResult.failure("Activation refusée : " + validation.reason() + ".");
        }

        ModuleConfiguration active;
        try {
            active = moduleApi.configure(guild.getId(), module, settings, ModuleStatus.ACTIVE, null, actorId);
        } catch (ApiException exception) {
            return rejected("Activation refusée : ", exception);
        }
        if (sharedConfigurationChanged(current, candidate)) {
            evictGuild(guild.getId());
        }
        putInCache(guild.getId(), active);
        return ConfigurationOperationResult.success("Le module est maintenant actif.");
    }

    public synchronized ConfigurationOperationResult configure(
            Guild guild,
            ModuleType module,
            String actorId,
            ModuleConfigurationSettings settings
    ) {
        ModuleConfiguration current = loadFresh(guild, module).orElse(null);
        if (current == null) {
            return ConfigurationOperationResult.failure("Configuration de module introuvable.");
        }

        ModuleConfiguration candidate = settings.applyTo(current);
        ModuleStatus status = null;
        String suspensionReason = null;
        ModuleValidationResult validation = validator.validate(guild, candidate);
        boolean suspendedByChange = false;
        if (current.status() == ModuleStatus.ACTIVE && !validation.valid()) {
            status = ModuleStatus.SUSPENDED;
            suspensionReason = validation.reason();
            suspendedByChange = true;
        }

        ModuleConfiguration configured;
        try {
            configured = moduleApi.configure(guild.getId(), module, settings, status, suspensionReason, actorId);
        } catch (ApiException exception) {
            return rejected("Configuration refusée : ", exception);
        }
        if (sharedConfigurationChanged(current, candidate)) {
            evictGuild(guild.getId());
        }
        putInCache(guild.getId(), configured);

        if (suspendedByChange) {
            return ConfigurationOperationResult.success(
                    "Les réglages sont enregistrés. Le module a été suspendu : "
                            + configured.suspensionReason() + ".");
        }
        return switch (configured.status()) {
            case ACTIVE -> ConfigurationOperationResult.success(
                    "Les réglages sont enregistrés. Le module reste actif.");
            case DISABLED -> ConfigurationOperationResult.success(
                    "Les réglages sont enregistrés. Le module reste désactivé.");
            case SUSPENDED -> ConfigurationOperationResult.success(
                    "Les réglages sont enregistrés. Le module reste suspendu : "
                            + configured.suspensionReason() + ". Relancez sa validation.");
        };
    }

    public synchronized ConfigurationOperationResult disable(
            Guild guild,
            ModuleType module,
            String actorId
    ) {
        if (loadFresh(guild, module).isEmpty()) {
            return ConfigurationOperationResult.failure("Configuration de module introuvable.");
        }
        ModuleConfiguration disabled = moduleApi.changeStatus(
                guild.getId(), module, ModuleStatus.DISABLED, null, actorId);
        putInCache(guild.getId(), disabled);
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
            current = moduleApi.changeStatus(
                    guild.getId(), module, ModuleStatus.SUSPENDED, validation.reason(), SYSTEM_ACTOR);
            putInCache(guild.getId(), current);
            return ConfigurationOperationResult.failure("Validation échouée : " + validation.reason() + ".");
        }

        ModuleConfiguration active;
        try {
            active = moduleApi.changeStatus(guild.getId(), module, ModuleStatus.ACTIVE, null, actorId);
        } catch (ApiException exception) {
            return rejected("Validation échouée : ", exception);
        }
        putInCache(guild.getId(), active);
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

        try {
            serverApi.setLogChannel(guild.getId(), channelId, actorId);
        } catch (ApiException exception) {
            return rejected("", exception);
        }
        evictGuild(guild.getId());
        list(guild);
        return ConfigurationOperationResult.success(channelId == null
                ? "Le salon de logs a été supprimé. Les modules qui en dépendent ont été suspendus."
                : "Le salon de logs a été mis à jour.");
    }

    public synchronized void evictGuild(String guildId) {
        cache.keySet().removeIf(key -> key.guildId().equals(guildId));
    }

    /**
     * Volontairement sans verrou : les listeners l'appellent à chaque événement, et un appel HTTP lent ou en
     * timeout ne doit pas bloquer les autres serveurs. Deux lectures simultanées d'une entrée expirée sont
     * inoffensives (GET idempotent).
     */
    private Optional<ModuleConfiguration> resolve(Guild guild, ModuleType module) {
        try {
            ModuleConfiguration configuration = cached(guild.getId(), module);
            if (configuration == null) {
                configuration = loadFresh(guild, module).orElse(null);
            }
            if (configuration == null) {
                return Optional.empty();
            }

            ModuleConfiguration validated = validateActive(guild, configuration);
            putInCache(guild.getId(), validated);
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
        Optional<ModuleConfiguration> configuration = moduleApi.find(guild.getId(), module);
        configuration.ifPresent(value -> putInCache(guild.getId(), value));
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
        return moduleApi.changeStatus(
                guild.getId(),
                configuration.module(),
                ModuleStatus.SUSPENDED,
                validation.reason(),
                SYSTEM_ACTOR);
    }

    /**
     * Une requête refusée par l'API (422) devient un échec présentable à l'administrateur ;
     * toute autre erreur reste une indisponibilité et remonte.
     */
    private ConfigurationOperationResult rejected(String prefix, ApiException exception) {
        if (!exception.isInvalidRequest()) {
            throw exception;
        }
        return ConfigurationOperationResult.failure(prefix + exception.describe());
    }

    private ModuleConfiguration cached(String guildId, ModuleType module) {
        CacheKey key = new CacheKey(guildId, module);
        CachedConfiguration entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (!entry.expiresAt().isAfter(Instant.now(clock))) {
            cache.remove(key, entry);
            return null;
        }
        return entry.configuration();
    }

    private void putInCache(String guildId, ModuleConfiguration configuration) {
        cache.put(
                new CacheKey(guildId, configuration.module()),
                new CachedConfiguration(configuration, Instant.now(clock).plus(CACHE_TTL)));
    }

    private boolean sharedConfigurationChanged(
            ModuleConfiguration current,
            ModuleConfiguration candidate
    ) {
        return !Objects.equals(current.logChannelId(), candidate.logChannelId())
                || !Objects.equals(
                current.roles(ModuleResourcePurpose.PROTECTED),
                candidate.roles(ModuleResourcePurpose.PROTECTED));
    }

    private record CacheKey(String guildId, ModuleType module) {
    }

    private record CachedConfiguration(ModuleConfiguration configuration, Instant expiresAt) {
    }
}
