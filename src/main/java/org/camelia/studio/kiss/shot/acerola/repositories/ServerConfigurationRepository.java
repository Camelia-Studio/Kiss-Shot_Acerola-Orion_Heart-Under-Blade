package org.camelia.studio.kiss.shot.acerola.repositories;

import org.camelia.studio.kiss.shot.acerola.db.HibernateConfig;
import org.camelia.studio.kiss.shot.acerola.models.ConfigurationHistory;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServer;
import org.camelia.studio.kiss.shot.acerola.models.ModuleResourcePurpose;
import org.camelia.studio.kiss.shot.acerola.models.ModuleStatus;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.ServerModule;
import org.camelia.studio.kiss.shot.acerola.models.ServerModuleChannel;
import org.camelia.studio.kiss.shot.acerola.models.ServerModuleRole;
import org.camelia.studio.kiss.shot.acerola.models.ServerSettings;
import org.camelia.studio.kiss.shot.acerola.services.configuration.ModuleConfiguration;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ServerConfigurationRepository {
    private final SessionFactory sessionFactory;

    public ServerConfigurationRepository() {
        this(HibernateConfig.getSessionFactory());
    }

    ServerConfigurationRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Optional<ModuleConfiguration> find(String discordId, ModuleType moduleType) {
        try (Session session = sessionFactory.openSession()) {
            return find(session, discordId, moduleType);
        }
    }

    public List<ModuleConfiguration> findAll(String discordId) {
        try (Session session = sessionFactory.openSession()) {
            List<ModuleConfiguration> configurations = new ArrayList<>();
            for (ModuleType moduleType : ModuleType.values()) {
                find(session, discordId, moduleType).ifPresent(configurations::add);
            }
            return configurations;
        }
    }

    public ModuleConfiguration activate(
            String discordId,
            ModuleConfiguration candidate,
            String actorId
    ) {
        return inTransaction(session -> {
            ServerModule module = requireModule(session, discordId, candidate.module());
            ModuleConfiguration previous = toConfiguration(session, module);

            replaceRoles(session, module, candidate.roleIds());
            replaceChannels(session, module, candidate.channelIds());
            updateLogChannel(session, discordId, candidate.logChannelId(), actorId);
            module.activate();

            auditIfChanged(session, module, actorId, "roles", format(previous.roleIds()), format(candidate.roleIds()));
            auditIfChanged(session, module, actorId, "channels", format(previous.channelIds()), format(candidate.channelIds()));
            auditIfChanged(session, module, actorId, "status", previous.status().name(), ModuleStatus.ACTIVE.name());

            return new ModuleConfiguration(
                    candidate.module(),
                    ModuleStatus.ACTIVE,
                    null,
                    candidate.logChannelId(),
                    candidate.roleIds(),
                    candidate.channelIds());
        });
    }

    public ModuleConfiguration changeStatus(
            String discordId,
            ModuleType moduleType,
            ModuleStatus status,
            String reason,
            String actorId
    ) {
        return inTransaction(session -> {
            ServerModule module = requireModule(session, discordId, moduleType);
            ModuleConfiguration previous = toConfiguration(session, module);

            switch (status) {
                case ACTIVE -> module.activate();
                case DISABLED -> module.disable();
                case SUSPENDED -> module.suspend(reason);
            }

            auditIfChanged(session, module, actorId, "status", previous.status().name(), status.name());
            if (status == ModuleStatus.SUSPENDED) {
                auditIfChanged(
                        session,
                        module,
                        actorId,
                        "suspension_reason",
                        previous.suspensionReason(),
                        reason);
            }
            return previous.withStatus(status, status == ModuleStatus.SUSPENDED ? reason : null);
        });
    }

    public void setLogChannel(String discordId, String channelId, String actorId) {
        inTransaction(session -> {
            updateLogChannel(session, discordId, channelId, actorId);
            return null;
        });
    }

    private void updateLogChannel(
            Session session,
            String discordId,
            String channelId,
            String actorId
    ) {
        ServerSettings settings = session.createQuery(
                        "FROM ServerSettings settings WHERE settings.server.discordId = :discordId",
                        ServerSettings.class)
                .setParameter("discordId", discordId)
                .getSingleResult();
        String previous = settings.getLogChannelId();
        settings.setLogChannelId(channelId);
        if (!Objects.equals(previous, channelId)) {
            session.persist(new ConfigurationHistory(
                    settingsServer(session, discordId),
                    actorId,
                    null,
                    "log_channel_id",
                    previous,
                    channelId));
        }
    }

    private Optional<ModuleConfiguration> find(Session session, String discordId, ModuleType moduleType) {
        return findModule(session, discordId, moduleType).map(module -> toConfiguration(session, module));
    }

    private ModuleConfiguration toConfiguration(Session session, ServerModule module) {
        String logChannelId = session.createQuery(
                        "SELECT settings.logChannelId FROM ServerSettings settings " +
                                "WHERE settings.server.id = :serverId",
                        String.class)
                .setParameter("serverId", module.getServer().getId())
                .uniqueResultOptional()
                .orElse(null);

        Map<ModuleResourcePurpose, Set<String>> roles = new EnumMap<>(ModuleResourcePurpose.class);
        for (Object[] row : session.createQuery(
                        "SELECT resource.purpose, resource.roleId FROM ServerModuleRole resource " +
                                "WHERE resource.serverModule.id = :moduleId",
                        Object[].class)
                .setParameter("moduleId", module.getId())
                .getResultList()) {
            roles.computeIfAbsent((ModuleResourcePurpose) row[0], ignored -> new LinkedHashSet<>())
                    .add((String) row[1]);
        }

        Map<ModuleResourcePurpose, Set<String>> channels = new EnumMap<>(ModuleResourcePurpose.class);
        for (Object[] row : session.createQuery(
                        "SELECT resource.purpose, resource.channelId FROM ServerModuleChannel resource " +
                                "WHERE resource.serverModule.id = :moduleId",
                        Object[].class)
                .setParameter("moduleId", module.getId())
                .getResultList()) {
            channels.computeIfAbsent((ModuleResourcePurpose) row[0], ignored -> new LinkedHashSet<>())
                    .add((String) row[1]);
        }

        return new ModuleConfiguration(
                module.getModule(),
                module.getStatus(),
                module.getSuspensionReason(),
                logChannelId,
                roles,
                channels);
    }

    private void replaceRoles(
            Session session,
            ServerModule module,
            Map<ModuleResourcePurpose, Set<String>> resources
    ) {
        session.createMutationQuery("DELETE FROM ServerModuleRole WHERE serverModule.id = :moduleId")
                .setParameter("moduleId", module.getId())
                .executeUpdate();
        resources.forEach((purpose, ids) -> ids.forEach(roleId ->
                session.persist(new ServerModuleRole(module, purpose, roleId))));
    }

    private void replaceChannels(
            Session session,
            ServerModule module,
            Map<ModuleResourcePurpose, Set<String>> resources
    ) {
        session.createMutationQuery("DELETE FROM ServerModuleChannel WHERE serverModule.id = :moduleId")
                .setParameter("moduleId", module.getId())
                .executeUpdate();
        resources.forEach((purpose, ids) -> ids.forEach(channelId ->
                session.persist(new ServerModuleChannel(module, purpose, channelId))));
    }

    private void auditIfChanged(
            Session session,
            ServerModule module,
            String actorId,
            String setting,
            String oldValue,
            String newValue
    ) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        session.persist(new ConfigurationHistory(
                module.getServer(),
                actorId,
                module.getModule(),
                setting,
                oldValue,
                newValue));
    }

    private Optional<ServerModule> findModule(Session session, String discordId, ModuleType moduleType) {
        return session.createQuery(
                        "SELECT module FROM ServerModule module JOIN FETCH module.server server " +
                                "WHERE server.discordId = :discordId AND module.module = :module",
                        ServerModule.class)
                .setParameter("discordId", discordId)
                .setParameter("module", moduleType)
                .uniqueResultOptional();
    }

    private ServerModule requireModule(Session session, String discordId, ModuleType moduleType) {
        return findModule(session, discordId, moduleType)
                .orElseThrow(() -> new IllegalStateException("Configuration de module introuvable"));
    }

    private DiscordServer settingsServer(
            Session session,
            String discordId
    ) {
        return session.createQuery(
                        "FROM DiscordServer WHERE discordId = :discordId",
                        DiscordServer.class)
                .setParameter("discordId", discordId)
                .getSingleResult();
    }

    private String format(Map<ModuleResourcePurpose, Set<String>> resources) {
        return resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getKey().name() + "=" + entry.getValue().stream().sorted().collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
    }

    private <T> T inTransaction(TransactionWork<T> work) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                T result = work.execute(session);
                transaction.commit();
                return result;
            } catch (RuntimeException exception) {
                transaction.rollback();
                throw exception;
            }
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T execute(Session session);
    }
}
