package org.camelia.studio.kiss.shot.acerola.repositories;

import org.camelia.studio.kiss.shot.acerola.db.HibernateConfig;
import org.camelia.studio.kiss.shot.acerola.models.LeaveSurvey;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.Optional;

public class LeaveSurveyRepository {
    private final SessionFactory sessionFactory;

    public LeaveSurveyRepository() {
        this.sessionFactory = HibernateConfig.getSessionFactory();
    }

    public LeaveSurvey save(LeaveSurvey survey) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(survey);
            session.getTransaction().commit();
            return survey;
        }
    }

    public Optional<LeaveSurvey> findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return Optional.ofNullable(session.find(LeaveSurvey.class, id));
        }
    }

    public void update(LeaveSurvey survey) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.merge(survey);
            session.getTransaction().commit();
        }
    }
}
