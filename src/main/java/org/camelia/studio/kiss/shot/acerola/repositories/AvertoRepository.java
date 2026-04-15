package org.camelia.studio.kiss.shot.acerola.repositories;

import org.camelia.studio.kiss.shot.acerola.db.HibernateConfig;
import org.camelia.studio.kiss.shot.acerola.models.Averto;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class AvertoRepository {
    private final SessionFactory sessionFactory;
    private static AvertoRepository instance;

    public static AvertoRepository getInstance() {
        if (instance == null) {
            instance = new AvertoRepository();
        }

        return instance;
    }

    public AvertoRepository() {
        this.sessionFactory = HibernateConfig.getSessionFactory();
    }

    public List<Averto> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Averto ORDER BY createdAt DESC", Averto.class)
                    .list();
        }
    }

    public List<Averto> findCount(int count) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Averto ORDER BY createdAt DESC", Averto.class)
                    .setMaxResults(count)
                    .list();
        }
    }

    public Averto save(Averto averto) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(averto);
            session.getTransaction().commit();
            return averto;
        }
    }
}
