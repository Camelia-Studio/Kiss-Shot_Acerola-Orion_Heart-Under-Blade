package org.camelia.studio.kiss.shot.acerola.repositories;

import org.camelia.studio.kiss.shot.acerola.db.HibernateConfig;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServer;
import org.camelia.studio.kiss.shot.acerola.models.DiscordServerLifecycle;
import org.camelia.studio.kiss.shot.acerola.models.ModuleType;
import org.camelia.studio.kiss.shot.acerola.models.ServerModule;
import org.camelia.studio.kiss.shot.acerola.models.ServerSettings;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class DiscordServerRepository {
    private final SessionFactory sessionFactory;

    public DiscordServerRepository() {
        this(HibernateConfig.getSessionFactory());
    }

    DiscordServerRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public DiscordServer activate(String discordId, Instant observedAt) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                DiscordServer server = activate(session, discordId, observedAt);
                transaction.commit();
                return server;
            } catch (RuntimeException exception) {
                transaction.rollback();
                throw exception;
            }
        }
    }

    public void markLeft(String discordId, Instant observedAt) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                findByDiscordId(session, discordId).ifPresent(server -> server.markLeft(observedAt));
                transaction.commit();
            } catch (RuntimeException exception) {
                transaction.rollback();
                throw exception;
            }
        }
    }

    public void synchronize(Collection<String> activeDiscordIds, Collection<String> unavailableDiscordIds, Instant observedAt) {
        Set<String> activeIds = new HashSet<>(activeDiscordIds);
        Set<String> unavailableIds = new HashSet<>(unavailableDiscordIds);

        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                for (String discordId : activeIds) {
                    activate(session, discordId, observedAt);
                }

                session.createQuery(
                                "FROM DiscordServer WHERE lifecycle = :lifecycle",
                                DiscordServer.class)
                        .setParameter("lifecycle", DiscordServerLifecycle.ACTIVE)
                        .getResultList()
                        .stream()
                        .filter(server -> !activeIds.contains(server.getDiscordId()))
                        .filter(server -> !unavailableIds.contains(server.getDiscordId()))
                        .forEach(server -> server.markLeft(observedAt));

                transaction.commit();
            } catch (RuntimeException exception) {
                transaction.rollback();
                throw exception;
            }
        }
    }

    public Optional<DiscordServer> findByDiscordId(String discordId) {
        try (Session session = sessionFactory.openSession()) {
            return findByDiscordId(session, discordId);
        }
    }

    public Optional<String> findLogChannelId(String discordId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "SELECT settings.logChannelId FROM ServerSettings settings " +
                                    "WHERE settings.server.discordId = :discordId",
                            String.class)
                    .setParameter("discordId", discordId)
                    .uniqueResultOptional();
        }
    }

    private DiscordServer activate(Session session, String discordId, Instant observedAt) {
        DiscordServer server = findByDiscordId(session, discordId).orElse(null);
        if (server == null) {
            server = new DiscordServer(discordId, observedAt);
            session.persist(server);
            session.persist(new ServerSettings(server));
            for (ModuleType module : ModuleType.values()) {
                session.persist(new ServerModule(server, module));
            }
        } else {
            server.markActive(observedAt);
        }
        return server;
    }

    private Optional<DiscordServer> findByDiscordId(Session session, String discordId) {
        return session.createQuery(
                        "FROM DiscordServer WHERE discordId = :discordId",
                        DiscordServer.class)
                .setParameter("discordId", discordId)
                .uniqueResultOptional();
    }
}
