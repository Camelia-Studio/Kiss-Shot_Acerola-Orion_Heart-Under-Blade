package org.camelia.studio.kiss.shot.acerola.repositories;

import jakarta.persistence.LockModeType;
import org.camelia.studio.kiss.shot.acerola.db.HibernateConfig;
import org.camelia.studio.kiss.shot.acerola.models.LeaveSurvey;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.time.Instant;
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

    public Optional<LeaveSurvey> markAsRespondedIfPending(Long id, String response) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                LeaveSurvey survey = session.find(LeaveSurvey.class, id, LockModeType.PESSIMISTIC_WRITE);
                if (survey == null || survey.isResponded()) {
                    tx.rollback();
                    return Optional.empty();
                }
                survey.setResponse(response);
                survey.setResponded(true);
                tx.commit();
                return Optional.of(survey);
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }

    public int deleteOlderThan(Instant threshold) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                int deleted = session.createMutationQuery(
                    "DELETE FROM LeaveSurvey s WHERE s.leftAt < :threshold"
                ).setParameter("threshold", threshold).executeUpdate();
                tx.commit();
                return deleted;
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        }
    }
}
