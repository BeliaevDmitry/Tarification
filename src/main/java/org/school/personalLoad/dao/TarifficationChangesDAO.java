package org.school.personalLoad.dao;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.TarifficationChanges;

import java.util.List;

public class TarifficationChangesDAO {

    public void saveAll(List<TarifficationChanges> entities) {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            for (int i = 0; i < entities.size(); i++) {
                session.save(entities.get(i));
                if (i % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }

    public List<TarifficationChanges> findAll() {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery("FROM TarifficationChanges ORDER BY changeDate DESC",
                    TarifficationChanges.class).list();
        }
    }



    public void deleteAllHistory() {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.createQuery("DELETE FROM TarifficationChanges").executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }
}