package org.school.personalLoad.service.impl;

import org.school.personalLoad.dao.TarifficationPersonDAO;
import org.school.personalLoad.dao.TarifficationChangesDAO;
import org.school.personalLoad.dao.NamingMeshDAO;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.config.DatabaseConfig;
import org.school.personalLoad.service.DatabaseService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class DatabaseServiceImpl implements DatabaseService {

    private final TarifficationPersonDAO currentDAO;
    private final TarifficationChangesDAO changesDAO;
    private final NamingMeshDAO namingMeshDAO;

    public DatabaseServiceImpl() {
        this.currentDAO = new TarifficationPersonDAO();
        this.changesDAO = new TarifficationChangesDAO();
        this.namingMeshDAO = new NamingMeshDAO();

        if (DatabaseConfig.CLEAR_HISTORY_ON_START) {
            System.out.println("🗑️ История очищена по запросу (CLEAR_HISTORY_ON_START = true)");
            fullReset();
        }
    }

    /**
     * Основной метод: сравнивает и сохраняет данные
     */
    public void compareAndSave(List<TarifficationPerson> newTariffication, List<NamingMesh> namingMeshes) {
        System.out.println("🔄 Начало сравнения и сохранения данных...");

        // 1. Сохраняем naming mesh если предоставлен
        if (namingMeshes != null && !namingMeshes.isEmpty()) {
            namingMeshDAO.saveAll(namingMeshes);
            System.out.println("💾 Сохранено записей в naming mesh: " + namingMeshes.size());
        }

        // 2. Сначала сравниваем с предыдущей версией
        List<TarifficationChanges> changes = compareWithHistory(newTariffication);

        // 3. Сохраняем изменения в историю (если включено)
        if (!changes.isEmpty() && DatabaseConfig.KEEP_HISTORY) {
            changesDAO.saveAll(changes);
            System.out.println("💾 Изменения тарификации сохранены в историю и содержат: "
                    + changes.size() + " записей");
        }

        // 4. Сохраняем новую версию тарификации
        saveCurrentTariffication(newTariffication);

        // 5. Обновляем связи с naming mesh
        currentDAO.updateAllNamingMeshRelations();

        System.out.println("✅ Новая тарификация сохранена в базу данных");
    }

    /**
     * Перегруженный метод для обратной совместимости
     */
    public void compareAndSave(List<TarifficationPerson> newTariffication) {
        compareAndSave(newTariffication, null);
    }

    /**
     * Сравниваем новую тарификацию с историей изменений
     */
    public List<TarifficationChanges> compareWithHistory(List<TarifficationPerson> newTariffication) {
        List<TarifficationChanges> allChanges = new ArrayList<>();
        List<TarifficationPerson> oldTariffications = currentDAO.findAll();

        System.out.println("📊 Начинаем сравнивать! в БД хранится " + oldTariffications.size()
                + " записей, в новой тарификации хранится " + newTariffication.size() + " записей");

        if (oldTariffications.isEmpty()) {
            System.out.println("⭐ Первая загрузка: " + newTariffication.size() +
                    " записей, изменений не найдено (история пуста)");
        } else {
            findChangesComparedToHistory(oldTariffications, newTariffication, allChanges);
            System.out.println("📈 Найдено изменений: " + allChanges.size());
        }

        return allChanges;
    }

    // Дополнительные методы для работы с историей
    public List<TarifficationChanges> getAllHistory() {
        return changesDAO.findAll();
    }

    public void saveCurrentTariffication(List<TarifficationPerson> tarifficationList) {
        currentDAO.saveAll(tarifficationList);
        System.out.println("💾 Сохранено записей в базу данных: " + tarifficationList.size());
    }

    public void fullReset() {
        changesDAO.deleteAllHistory();
        currentDAO.deleteAll();
        namingMeshDAO.deleteAll();
        System.out.println("✅ Полный сброс: история, текущая тарификация и naming mesh очищены");
    }

    public List<TarifficationPerson> findAllByFieldsHistory(String subject, String className, String NumberSchoolBuilding) {
        List<TarifficationPerson> oldTariffications = currentDAO.findAll();
        return oldTariffications.stream()
                .filter(person -> person.getSubjectName().equals(subject) && person.getClassName().equals(className)
                        && person.getNumberSchoolBuilding().equals(NumberSchoolBuilding))
                .collect(Collectors.toList());
    }

    public List<String> findAllUniqueClassAndGroupNames() {
        List<TarifficationPerson> allPersons = currentDAO.findAll();
        Set<String> uniqueNames = new HashSet<>();

        for (TarifficationPerson person : allPersons) {
            if (person.getClassName() != null && !person.getClassName().trim().isEmpty()) {
                uniqueNames.add(person.getClassName().trim());
            }
            if (person.getGroupNameEducationalPlan() != null && !person.getGroupNameEducationalPlan().trim().isEmpty()) {
                uniqueNames.add(person.getGroupNameEducationalPlan().trim());
            }
        }

        List<String> result = new ArrayList<>(uniqueNames);
        Collections.sort(result);
        return result;
    }

    /**
     * Новые методы для работы с NamingMesh
     */
    public List<NamingMesh> getAllNamingMeshes() {
        return namingMeshDAO.findAll();
    }

    public void saveNamingMeshes(List<NamingMesh> namingMeshes) {
        namingMeshDAO.saveAll(namingMeshes);
        System.out.println("💾 Сохранено записей в naming mesh: " + namingMeshes.size());

        // Обновляем связи после сохранения новых mesh
        currentDAO.updateAllNamingMeshRelations();
    }

    public Optional<NamingMesh> findNamingMesh(String subjectName, String className, String groupNameEducationalPlan) {
        return namingMeshDAO.findById(subjectName, className, groupNameEducationalPlan);
    }

    public List<TarifficationPerson> findAllPersonsWithMesh() {
        return currentDAO.findAll();
    }

    public List<TarifficationPerson> findPersonsByTeacherWithMesh(String fioTeacher) {
        return currentDAO.findByTeacher(fioTeacher);
    }

    public void updateNamingMeshRelations() {
        currentDAO.updateAllNamingMeshRelations();
        System.out.println("🔗 Обновлены связи с naming mesh");
    }

    private TarifficationChanges createHistoryRecord(TarifficationPerson current,
                                                     TarifficationChanges.ChangeType changeType) {
        TarifficationChanges history = new TarifficationChanges();
        history.setFioTeacher(current.getFioTeacher() != null ? current.getFioTeacher() : "");
        history.setNumberSchoolBuilding(current.getNumberSchoolBuilding() != null ? current.getNumberSchoolBuilding() : "");
        history.setSubjectName(current.getSubjectName() != null ? current.getSubjectName() : "");
        history.setClassName(current.getClassName() != null ? current.getClassName() : "");
        history.setLoad(current.getLoad());
        history.setGroupNameEducationalPlan(current.getGroupNameEducationalPlan() != null ? current.getGroupNameEducationalPlan() : "");
        history.setGroupLoad(current.getGroupLoad() != null ? current.getGroupLoad() : 0);
        history.setChangeType(changeType);
        history.setChangeDate(LocalDateTime.now());
        return history;
    }

    private void findChangesComparedToHistory(List<TarifficationPerson> oldTariffications,
                                              List<TarifficationPerson> newTariffication,
                                              List<TarifficationChanges> changes) {

        Map<String, TarifficationPerson> historyMap = createPersonMap(oldTariffications);
        Map<String, TarifficationPerson> newMap = createPersonMap(newTariffication);

        // Добавляем удаления
        for (Map.Entry<String, TarifficationPerson> entry : historyMap.entrySet()) {
            String key = entry.getKey();
            if (!newMap.containsKey(key)) {
                changes.add(createHistoryRecord(entry.getValue(),
                        TarifficationChanges.ChangeType.REMOVED));
            }
        }

        // Проверяем добавленные записи
        for (Map.Entry<String, TarifficationPerson> entry : newMap.entrySet()) {
            String key = entry.getKey();
            if (!historyMap.containsKey(key)) {
                changes.add(createHistoryRecord(entry.getValue(),
                        TarifficationChanges.ChangeType.ADDED));
            }
        }

        // Проверяем измененные записи
        for (Map.Entry<String, TarifficationPerson> entry : newMap.entrySet()) {
            String key = entry.getKey();
            if (historyMap.containsKey(key)) {
                TarifficationPerson newPerson = entry.getValue();
                TarifficationPerson oldPerson = historyMap.get(key);

                boolean loadChanged = !Objects.equals(newPerson.getLoad(), oldPerson.getLoad());
                boolean groupLoadChanged = !Objects.equals(
                        newPerson.getGroupLoad() != null ? newPerson.getGroupLoad() : 0,
                        oldPerson.getGroupLoad() != null ? oldPerson.getGroupLoad() : 0
                );

                if (loadChanged || groupLoadChanged) {
                    changes.add(createHistoryRecord(newPerson,
                            TarifficationChanges.ChangeType.MODIFIED));
                }
            }
        }
    }

    private Map<String, TarifficationPerson> createPersonMap(List<TarifficationPerson> persons) {
        Map<String, TarifficationPerson> map = new HashMap<>();
        for (TarifficationPerson person : persons) {
            map.put(createKey(person), person);
        }
        return map;
    }

    private String createKey(TarifficationPerson person) {
        return createKey(person.getFioTeacher(), person.getNumberSchoolBuilding(),
                person.getSubjectName(), person.getClassName(), person.getGroupNameEducationalPlan());
    }

    private String createKey(String fio, String building, String subject, String className, String group) {
        return normalizeString(fio) + "|" +
                normalizeString(building) + "|" +
                normalizeString(subject) + "|" +
                normalizeString(className) + "|" +
                normalizeString(Objects.toString(group, ""));
    }

    private String normalizeString(String str) {
        if (str == null) return "";
        return str.trim().toLowerCase();
    }
}