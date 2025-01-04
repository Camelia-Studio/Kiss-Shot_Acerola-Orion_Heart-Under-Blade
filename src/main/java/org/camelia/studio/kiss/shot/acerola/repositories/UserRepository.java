package org.camelia.studio.kiss.shot.acerola.repositories;

import org.camelia.studio.kiss.shot.acerola.db.HibernateConfig;
import org.camelia.studio.kiss.shot.acerola.models.User;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class UserRepository {
    private final SessionFactory sessionFactory;
    private static UserRepository instance;

    public static UserRepository getInstance() {
        if (instance == null) {
            instance = new UserRepository();
        }

        return instance;
    }

    public UserRepository() {
        this.sessionFactory = HibernateConfig.getSessionFactory();
    }

    public List<User> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM User", User.class).list();
        }
    }

    public User findByDiscordId(String discordId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM User WHERE discordId = :discordId", User.class)
                    .setParameter("discordId", discordId)
                    .uniqueResult();
        }
    }

    public User save(User user) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(user);
            session.getTransaction().commit();
            return user;
        }
    }

    public void update(User user) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.merge(user);
            session.getTransaction().commit();
        }
    }
}
