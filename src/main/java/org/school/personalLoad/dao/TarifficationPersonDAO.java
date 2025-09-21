package org.school.personalLoad.dao;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.List;
import java.util.Optional;

public class TarifficationPersonDAO {

    private final NamingMeshDAO namingMeshDAO = new NamingMeshDAO();

    public void saveAll(List<TarifficationPerson> entities) {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Очищаем таблицу перед сохранением новых данных
            session.createQuery("DELETE FROM TarifficationPerson").executeUpdate();

            // Сохраняем все новые записи
            for (int i = 0; i < entities.size(); i++) {
                TarifficationPerson person = entities.get(i);

                // Устанавливаем связь с NamingMesh если есть соответствие
                establishNamingMeshRelation(person, session);

                session.save(person);

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
            return session.createQuery(
                    "SELECT DISTINCT tp FROM TarifficationPerson tp " +
                            "LEFT JOIN FETCH tp.namingMesh",
                    TarifficationPerson.class
            ).list();
        }
    }

    public List<TarifficationPerson> findByTeacher(String fioTeacher) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT tp FROM TarifficationPerson tp " +
                            "LEFT JOIN FETCH tp.namingMesh " +
                            "WHERE tp.fioTeacher = :fioTeacher",
                    TarifficationPerson.class
            ).setParameter("fioTeacher", fioTeacher).list();
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

    /**
     * Устанавливает связь с NamingMesh если найдено соответствие
     */
    private void establishNamingMeshRelation(TarifficationPerson person, Session session) {
        if (person.getSubjectName() != null &&
                person.getClassName() != null &&
                person.getGroupNameEducationalPlan() != null) {

            String hql = "FROM NamingMesh WHERE subjectName = :subjectName " +
                    "AND className = :className " +
                    "AND groupNameEducationalPlan = :groupName";

            Optional<NamingMesh> namingMesh = session.createQuery(hql, NamingMesh.class)
                    .setParameter("subjectName", person.getSubjectName())
                    .setParameter("className", person.getClassName())
                    .setParameter("groupName", person.getGroupNameEducationalPlan())
                    .uniqueResultOptional();

            namingMesh.ifPresent(person::setNamingMesh);
        }
    }

    /**
     * Обновляет связи всех записей с NamingMesh
     */
    public void updateAllNamingMeshRelations() {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            List<TarifficationPerson> allPersons = session.createQuery(
                    "FROM TarifficationPerson", TarifficationPerson.class).list();

            for (TarifficationPerson person : allPersons) {
                establishNamingMeshRelation(person, session);
                session.update(person);
            }

            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }
}