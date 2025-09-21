package org.school.personalLoad.dao;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.NamingMesh;

import java.util.List;
import java.util.Optional;

public class NamingMeshDAO {

    public void saveAll(List<NamingMesh> entities) {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            for (int i = 0; i < entities.size(); i++) {
                session.saveOrUpdate(entities.get(i));
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

    public List<NamingMesh> findAll() {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery("FROM NamingMesh", NamingMesh.class).list();
        }
    }

    public Optional<NamingMesh> findById(String subjectName, String className, String groupNameEducationalPlan) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            String hql = "FROM NamingMesh WHERE subjectName = :subjectName " +
                    "AND className = :className " +
                    "AND groupNameEducationalPlan = :groupName";

            return session.createQuery(hql, NamingMesh.class)
                    .setParameter("subjectName", subjectName)
                    .setParameter("className", className)
                    .setParameter("groupName", groupNameEducationalPlan)
                    .uniqueResultOptional();
        }
    }

    public void deleteAll() {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.createQuery("DELETE FROM NamingMesh").executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }

    public void save(NamingMesh entity) {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.saveOrUpdate(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }

    // В классе NamingMeshDAO добавим:
    public void delete(NamingMesh entity) {
        Transaction transaction = null;
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.delete(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw new RuntimeException("Ошибка при удалении naming mesh", e);
        }
    }
}