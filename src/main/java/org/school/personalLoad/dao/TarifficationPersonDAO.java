package org.school.personalLoad.dao;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.model.TarifficationPerson;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class TarifficationPersonDAO {

    public void saveAll(List<TarifficationPerson> entities) {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            log.info("Начинаем обработку {} записей тарификации", entities.size());

            Map<String, NamingMesh> namingMeshCache = loadNamingMeshCache(session);
            int createdMeshCount = 0;
            int existingMeshCount = 0;

            for (int i = 0; i < entities.size(); i++) {
                TarifficationPerson person = entities.get(i);
                boolean created = establishNamingMeshRelation(person, session, namingMeshCache);

                if (created) {
                    createdMeshCount++;
                } else if (person.getNamingMesh() != null) {
                    existingMeshCount++;
                }

                if (i % 100 == 0) {
                    session.flush();
                    session.clear();
                    log.debug("Обработано {} записей...", i);
                }
            }

            session.createQuery("DELETE FROM TarifficationPerson").executeUpdate();

            for (int i = 0; i < entities.size(); i++) {
                session.save(entities.get(i));
                if (i % 100 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            transaction.commit();
            log.info("Успешно сохранено {} записей тарификации", entities.size());
            log.info("Статистика naming_mesh: {} существующих, {} созданных новых", existingMeshCount, createdMeshCount);

        } catch (Exception e) {
            log.error("Ошибка при сохранении записей тарификации", e);
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception rollbackEx) {
                    log.error("Ошибка при откате транзакции", rollbackEx);
                }
            }
            throw new RuntimeException("Failed to save tariffication data", e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public List<TarifficationPerson> findAll() {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT tp FROM TarifficationPerson tp LEFT JOIN FETCH tp.namingMesh",
                    TarifficationPerson.class
            ).list();
        }
    }

    public List<TarifficationPerson> findByTeacher(String fioTeacher) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT tp FROM TarifficationPerson tp LEFT JOIN FETCH tp.namingMesh WHERE tp.fioTeacher = :fioTeacher",
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
            if (transaction != null) {
                transaction.rollback();
            }
            throw e;
        }
    }

    private boolean establishNamingMeshRelation(TarifficationPerson person, Session session, Map<String, NamingMesh> cache) {
        try {
            if (person.getSubjectName() == null || person.getClassName() == null || person.getClassName().isEmpty()) {
                person.setNamingMesh(null);
                return false;
            }

            String subjectName = person.getSubjectName().trim();
            String className = person.getClassName().trim();
            String groupName = person.getGroupNameEducationalPlan() == null ? "" : person.getGroupNameEducationalPlan().trim();
            String key = cacheKey(subjectName, className, groupName);

            NamingMesh existing = cache.get(key);
            if (existing != null) {
                person.setNamingMesh(existing);
                return false;
            }

            NamingMesh newMesh = new NamingMesh();
            newMesh.setClassName(className);
            newMesh.setGroupNameEducationalPlan(groupName);
            newMesh.setSubjectName(subjectName);

            session.save(newMesh);
            session.flush();

            cache.put(key, newMesh);
            person.setNamingMesh(newMesh);
            return true;

        } catch (Exception e) {
            log.error("Ошибка при установке связи NamingMesh для {} - {}", person.getClassName(), person.getSubjectName(), e);
            person.setNamingMesh(null);
            return false;
        }
    }

    public void updateAllNamingMeshRelations() {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            List<TarifficationPerson> allPersons = session.createQuery(
                    "FROM TarifficationPerson", TarifficationPerson.class).list();
            Map<String, NamingMesh> namingMeshCache = loadNamingMeshCache(session);

            int withMesh = 0;
            int withoutMesh = 0;
            int createdMesh = 0;

            for (TarifficationPerson person : allPersons) {
                boolean created = establishNamingMeshRelation(person, session, namingMeshCache);
                session.update(person);

                if (person.getNamingMesh() != null) {
                    withMesh++;
                    if (created) {
                        createdMesh++;
                    }
                } else {
                    withoutMesh++;
                }
            }

            transaction.commit();
            log.info("Обновлены связи: {} с naming_mesh ({} новых создано), {} без naming_mesh", withMesh, createdMesh, withoutMesh);

        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            log.error("Ошибка при обновлении связей", e);
            throw e;
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public boolean checkNamingMeshExists(String className, String groupName, String subjectName) {
        try (Session session = HibernateConfig.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(*) FROM NamingMesh WHERE subjectName = :subjectName " +
                    "AND className = :className " +
                    "AND (groupNameEducationalPlan = :groupName OR " +
                    "(groupNameEducationalPlan IS NULL AND :groupName = '') OR " +
                    "(groupNameEducationalPlan = '' AND :groupName = ''))";

            Long count = session.createQuery(hql, Long.class)
                    .setParameter("subjectName", subjectName != null ? subjectName.trim() : "")
                    .setParameter("className", className != null ? className.trim() : "")
                    .setParameter("groupName", groupName != null ? groupName.trim() : "")
                    .uniqueResult();

            return count != null && count > 0;
        }
    }

    private Map<String, NamingMesh> loadNamingMeshCache(Session session) {
        List<NamingMesh> meshList = session.createQuery("FROM NamingMesh", NamingMesh.class).list();
        Map<String, NamingMesh> cache = new HashMap<>(Math.max(16, meshList.size() * 2));
        for (NamingMesh mesh : meshList) {
            cache.put(cacheKey(mesh.getSubjectName(), mesh.getClassName(), mesh.getGroupNameEducationalPlan()), mesh);
        }
        return cache;
    }

    private String cacheKey(String subjectName, String className, String groupName) {
        return normalize(subjectName) + "|" + normalize(className) + "|" + normalize(groupName);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
