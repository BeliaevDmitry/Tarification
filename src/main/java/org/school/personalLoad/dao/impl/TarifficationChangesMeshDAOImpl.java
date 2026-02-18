package org.school.personalLoad.dao.impl;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.dao.TarifficationChangesMeshDAO;
import org.school.personalLoad.model.TarifficationChangesMesh;

import java.util.List;

public class TarifficationChangesMeshDAOImpl implements TarifficationChangesMeshDAO {

    @Override
    public void save(TarifficationChangesMesh change) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.save(change);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    System.err.println("Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Ошибка при сохранении изменения МЭШ", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    @Override
    public void saveAll(List<TarifficationChangesMesh> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            for (int i = 0; i < changes.size(); i++) {
                session.save(changes.get(i));
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
            throw new RuntimeException("Ошибка при сохранении списка изменений МЭШ", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    @Override
    public List<TarifficationChangesMesh> findAll() {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery("FROM TarifficationChangesMesh ORDER BY changeDate DESC",
                    TarifficationChangesMesh.class).list();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении всех изменений МЭШ", e);
        }
    }

    @Override
    public List<TarifficationChangesMesh> findByTarifficationChangeId(Long changeId) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM TarifficationChangesMesh WHERE tarifficationChangeId = :changeId ORDER BY changeDate DESC",
                            TarifficationChangesMesh.class)
                    .setParameter("changeId", changeId)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при поиске изменений по ID тарификации", e);
        }
    }

    @Override
    public List<TarifficationChangesMesh> findByFioTeacher(String fioTeacher) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM TarifficationChangesMesh WHERE fioTeacher = :fioTeacher ORDER BY changeDate DESC",
                            TarifficationChangesMesh.class)
                    .setParameter("fioTeacher", fioTeacher)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при поиске изменений по ФИО преподавателя", e);
        }
    }

    @Override
    public List<TarifficationChangesMesh> findByGroupNameMesh(String groupNameMesh) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM TarifficationChangesMesh WHERE oldGroupNameMesh = :name OR newGroupNameMesh = :name ORDER BY changeDate DESC",
                            TarifficationChangesMesh.class)
                    .setParameter("name", groupNameMesh)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при поиске изменений по названию группы МЭШ", e);
        }
    }

    @Override
    public List<TarifficationChangesMesh> findByMeshChangeType(TarifficationChangesMesh.MeshChangeType changeType) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM TarifficationChangesMesh WHERE meshChangeType = :changeType ORDER BY changeDate DESC",
                            TarifficationChangesMesh.class)
                    .setParameter("changeType", changeType)
                    .list();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при поиске изменений по типу изменения", e);
        }
    }

    @Override
    public void deleteAll() {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.createQuery("DELETE FROM TarifficationChangesMesh").executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    System.err.println("Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Ошибка при удалении всех изменений МЭШ", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
}