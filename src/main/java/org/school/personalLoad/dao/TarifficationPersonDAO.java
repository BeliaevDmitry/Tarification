package org.school.personalLoad.dao;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.List;
import java.util.Map;

public class TarifficationPersonDAO {

    public void saveAll(List<TarifficationPerson> entities) {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Очищаем таблицу перед сохранением новых данных
            session.createQuery("DELETE FROM TarifficationPerson").executeUpdate();

            // Сохраняем все новые записи
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

    public List<TarifficationPerson> findAll() {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery("FROM TarifficationPerson", TarifficationPerson.class).list();
        }
    }

    public void deleteAll() {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.createQuery("DELETE FROM TarifficationPerson").executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }
}