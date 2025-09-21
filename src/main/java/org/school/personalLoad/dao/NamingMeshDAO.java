package org.school.personalLoad.dao;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.NamingMesh;

import java.util.List;
import java.util.Optional;

public class NamingMeshDAO {

    public void save(NamingMesh namingMesh) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.save(namingMesh);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    System.err.println("Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Ошибка при сохранении NamingMesh", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void saveAll(List<NamingMesh> namingMeshes) {
        if (namingMeshes == null || namingMeshes.isEmpty()) {
            return;
        }

        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            for (int i = 0; i < namingMeshes.size(); i++) {
                session.save(namingMeshes.get(i));
                if (i % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    System.err.println("Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Ошибка при сохранении списка NamingMesh", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public List<NamingMesh> findAll() {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery("FROM NamingMesh", NamingMesh.class).list();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении всех NamingMesh", e);
        }
    }

    public Optional<NamingMesh> findById(String subjectName, String className, String groupNameEducationalPlan) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM NamingMesh WHERE subjectName = :subjectName AND className = :className AND groupNameEducationalPlan = :groupName",
                            NamingMesh.class)
                    .setParameter("subjectName", subjectName)
                    .setParameter("className", className)
                    .setParameter("groupName", groupNameEducationalPlan)
                    .uniqueResultOptional();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при поиске NamingMesh", e);
        }
    }

    public void delete(NamingMesh namingMesh) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.delete(namingMesh);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    System.err.println("Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Ошибка при удалении NamingMesh", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void deleteAll() {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.createQuery("DELETE FROM NamingMesh").executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    System.err.println("Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Ошибка при удалении всех NamingMesh", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
    public void update(NamingMesh namingMesh) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.update(namingMesh); // Используем update вместо save
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    System.err.println("Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Ошибка при обновлении NamingMesh", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

}