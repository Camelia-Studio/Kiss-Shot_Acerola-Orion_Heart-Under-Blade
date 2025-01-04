package org.camelia.studio.kiss.shot.acerola.repositories;

import org.camelia.studio.kiss.shot.acerola.db.HibernateConfig;
import org.camelia.studio.kiss.shot.acerola.models.Averto;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Order;
import org.hibernate.query.SortDirection;

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
            return session.createQuery("FROM User", Averto.class)
                    .setOrder(Order.by(Averto.class, "createdAt", SortDirection.DESCENDING))
                    .list();
        }
    }

    public List<Averto> findCount(int count) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Averto", Averto.class)
                    .setOrder(Order.by(Averto.class, "createdAt", SortDirection.DESCENDING))
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
