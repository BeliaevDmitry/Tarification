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
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            System.out.println("🔄 Начинаем обработку " + entities.size() + " записей тарификации...");

            // Устанавливаем связи для всех записей (автоматически создаем недостающие naming_mesh)
            int createdMeshCount = 0;
            int existingMeshCount = 0;

            for (int i = 0; i < entities.size(); i++) {
                TarifficationPerson person = entities.get(i);
                boolean created = establishNamingMeshRelation(person, session);

                if (created) {
                    createdMeshCount++;
                } else if (person.getNamingMesh() != null) {
                    existingMeshCount++;
                }

                if (i % 50 == 0) {
                    session.flush();
                    session.clear();
                    System.out.println("⏳ Обработано " + i + " записей...");
                }
            }

            // Очищаем таблицу перед сохранением новых данных
            System.out.println("🧹 Очищаем старые записи...");
            session.createQuery("DELETE FROM TarifficationPerson").executeUpdate();

            // Сохраняем ВСЕ записи
            System.out.println("💾 Сохраняем все записи...");
            for (int i = 0; i < entities.size(); i++) {
                TarifficationPerson person = entities.get(i);
                session.save(person);

                if (i % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            transaction.commit();
            System.out.println("✅ Успешно сохранено " + entities.size() + " записей тарификации");
            System.out.println("📊 Статистика naming_mesh: " + existingMeshCount + " существующих, " +
                    createdMeshCount + " созданных новых");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при сохранении записей тарификации: " + e.getMessage());
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                    System.out.println("🔙 Транзакция откачена");
                } catch (Exception rollbackEx) {
                    System.err.println("❌ Ошибка при откате транзакции: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Failed to save tariffication data", e);
        } finally {
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                } catch (Exception closeEx) {
                    System.err.println("❌ Ошибка при закрытии сессии: " + closeEx.getMessage());
                }
            }
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
     * Если соответствие не найдено - автоматически создает новую запись в naming_mesh
     * @return true если была создана новая запись, false если использована существующая
     */
    private boolean establishNamingMeshRelation(TarifficationPerson person, Session session) {
        try {
            if (person.getSubjectName() != null &&
                    person.getClassName() != null &&
                    !person.getClassName().isEmpty()) {

                String groupName = person.getGroupNameEducationalPlan() != null ?
                        person.getGroupNameEducationalPlan() : "";

                // Ищем точное соответствие
                String hql = "FROM NamingMesh WHERE subjectName = :subjectName " +
                        "AND className = :className " +
                        "AND (groupNameEducationalPlan = :groupName OR " +
                        "(groupNameEducationalPlan IS NULL AND :groupName = '') OR " +
                        "(groupNameEducationalPlan = '' AND :groupName = ''))";

                Optional<NamingMesh> namingMesh = session.createQuery(hql, NamingMesh.class)
                        .setParameter("subjectName", person.getSubjectName().trim())
                        .setParameter("className", person.getClassName().trim())
                        .setParameter("groupName", groupName.trim())
                        .uniqueResultOptional();

                if (namingMesh.isPresent()) {
                    // Нашли существующую запись - устанавливаем связь
                    person.setNamingMesh(namingMesh.get());
                    return false; // не создавали новую запись
                } else {
                    // Соответствие не найдено - создаем новую запись в naming_mesh
                    NamingMesh newMesh = new NamingMesh();
                    newMesh.setClassName(person.getClassName().trim());
                    newMesh.setGroupNameEducationalPlan(groupName.trim());
                    newMesh.setSubjectName(person.getSubjectName().trim());

                    session.save(newMesh);
                    session.flush(); // Принудительно сохраняем чтобы получить ID

                    person.setNamingMesh(newMesh);
                    System.out.println("➕ Создана новая запись naming_mesh для: " +
                            person.getClassName() + " - " + person.getSubjectName() +
                            (groupName.isEmpty() ? "" : " (группа: " + groupName + ")"));
                    return true; // создали новую запись
                }
            } else {
                // Если нет необходимых данных для поиска - не устанавливаем связь
                person.setNamingMesh(null);
                System.out.println("⚠️ Не удалось установить связь (недостаточно данных): " +
                        "className=" + person.getClassName() + ", subjectName=" + person.getSubjectName());
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка при установке связи NamingMesh для: " +
                    person.getClassName() + " - " + person.getSubjectName());
            e.printStackTrace();
            // В случае ошибки не устанавливаем связь
            person.setNamingMesh(null);
            return false;
        }
    }

    /**
     * Обновляет связи всех записей с NamingMesh
     * Автоматически создает недостающие записи в naming_mesh
     */
    public void updateAllNamingMeshRelations() {
        Transaction transaction = null;
        Session session = null;
        try {
            session = HibernateConfig.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            List<TarifficationPerson> allPersons = session.createQuery(
                    "FROM TarifficationPerson", TarifficationPerson.class).list();

            int withMesh = 0;
            int withoutMesh = 0;
            int createdMesh = 0;

            for (TarifficationPerson person : allPersons) {
                boolean created = establishNamingMeshRelation(person, session);
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
            System.out.println("🔗 Обновлены связи: " + withMesh + " с naming_mesh (" +
                    createdMesh + " новых создано), " + withoutMesh + " без naming_mesh");

        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            System.err.println("❌ Ошибка при обновлении связей: " + e.getMessage());
            throw e;
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    /**
     * Вспомогательный метод для проверки существования записи в naming_mesh
     */
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
}