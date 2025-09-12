package org.school.personalLoad.service.impl;

import org.school.personalLoad.dao.TarifficationPersonDAO;
import org.school.personalLoad.dao.TarifficationChangesDAO;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.config.DatabaseConfig;
import org.school.personalLoad.service.DatabaseService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class DatabaseServiceImpl implements DatabaseService {

    private final TarifficationPersonDAO currentDAO;
    private final TarifficationChangesDAO ChangesDAO;

    public DatabaseServiceImpl() {
        this.currentDAO = new TarifficationPersonDAO();
        this.ChangesDAO = new TarifficationChangesDAO();

        if (DatabaseConfig.CLEAR_HISTORY_ON_START) {
            System.out.println("🗑️ История очищена по запросу (CLEAR_HISTORY_ON_START = true)");
            fullReset();
        }
    }

    /**
     * Основной метод: сравнивает и сохраняет данные
     */
    public List<TarifficationChanges> compareAndSave(List<TarifficationPerson> newTariffication) {
        System.out.println("🔄 Начало сравнения и сохранения данных...");

        // 1. Сначала сравниваем с предыдущей версией
        List<TarifficationChanges> changes = compareWithHistory(newTariffication);

        // 2. Сохраняем изменения в историю (если включено)
        if (!changes.isEmpty() && DatabaseConfig.KEEP_HISTORY) {
            ChangesDAO.saveAll(changes);
            System.out.println("💾 Изменения сохранены в историю: " + changes.size() + " записей");
        }

        // 3. Сохраняем новую версию тарификации
        saveCurrentTariffication(newTariffication);

        System.out.println("✅ Данные сохранены, найдено изменений: " + changes.size());
        return changes;
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
            // Первая загрузка - все записи новые

            System.out.println("⭐ Первая загрузка: " + newTariffication.size() +
                    " записей, изменений не найдено (история пуста)");
        } else {
            // Сравниваем с историей
            findChangesComparedToHistory(oldTariffications, newTariffication, allChanges);
            System.out.println("📈 Найдено изменений: " + allChanges.size());

        }

        return allChanges;
    }

    // Дополнительные методы для работы с историей
    public List<TarifficationChanges> getAllHistory() {
        return ChangesDAO.findAll();
    }

    public void saveCurrentTariffication(List<TarifficationPerson> tarifficationList) {

        currentDAO.saveAll(tarifficationList);
        System.out.println("💾 Сохранено записей в текущую тарификацию: " + tarifficationList.size());
    }

    public void fullReset() {
        ChangesDAO.deleteAllHistory();
        currentDAO.deleteAll();
        System.out.println("✅ Полный сброс: история и текущая тарификация очищены");
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

        // Используем Set для автоматического обеспечения уникальности
        Set<String> uniqueNames = new HashSet<>();

        for (TarifficationPerson person : allPersons) {
            // Добавляем className (если не null и не пустой)
            if (person.getClassName() != null && !person.getClassName().trim().isEmpty()) {
                uniqueNames.add(person.getClassName().trim());
            }

            // Добавляем groupName (если не null и не пустой)
            if (person.getGroupName() != null && !person.getGroupName().trim().isEmpty()) {
                uniqueNames.add(person.getGroupName().trim());
            }
        }

        // Преобразуем Set в отсортированный List
        List<String> result = new ArrayList<>(uniqueNames);
        Collections.sort(result);

        return result;
    }


    private TarifficationChanges createHistoryRecord(TarifficationPerson current,
                                                     TarifficationChanges.ChangeType changeType) {
        TarifficationChanges history = new TarifficationChanges();
        history.setFioTeacher(current.getFioTeacher() != null ? current.getFioTeacher() : "");
        history.setNumberSchoolBuilding(current.getNumberSchoolBuilding() != null ? current.getNumberSchoolBuilding() : "");
        history.setSubjectName(current.getSubjectName() != null ? current.getSubjectName() : "");
        history.setClassName(current.getClassName() != null ? current.getClassName() : "");
        history.setLoad(current.getLoad());
        history.setGroupName(current.getGroupName() != null ? current.getGroupName() : "");
        history.setGroupLoad(current.getGroupLoad() != null ? current.getGroupLoad() : 0);
        history.setChangeType(changeType);
        history.setChangeDate(LocalDateTime.now());
        return history;
    }

    /**
     * Находим изменения по сравнению со старой тарификацией
     */
    private void findChangesComparedToHistory(List<TarifficationPerson> oldTariffications,
                                              List<TarifficationPerson> newTariffication,
                                              List<TarifficationChanges> changes) {


        // 1. Создаём мапы для быстрого поиска
        Map<String, TarifficationPerson> historyMap = createPersonMap(oldTariffications);
        Map<String, TarifficationPerson> newMap = createPersonMap(newTariffication);

        // 2. Добавляем все удаления
        for (Map.Entry<String, TarifficationPerson> entry : historyMap.entrySet()) {
            String key = entry.getKey();
            if (!newMap.containsKey(key)) {
                // Запись удалена
                TarifficationPerson deletedRecord = entry.getValue();
                changes.add(createHistoryRecord(deletedRecord,
                        TarifficationChanges.ChangeType.REMOVED));
            }
        }

        // 3. Проверяем добавленные записи - есть в новых данных, но нет в истории
        for (Map.Entry<String, TarifficationPerson> entry : newMap.entrySet()) {
            String key = entry.getKey();
            if (!historyMap.containsKey(key)) {
                // Запись добавлена - создаем историческую запись из CurrentTariffication
                TarifficationPerson newPerson = entry.getValue();
                changes.add(createHistoryRecord(newPerson, TarifficationChanges.ChangeType.ADDED));
            }
        }

        // 4. Проверяем измененные записи - ключи совпадают, но поля load или groupLoad изменились
        for (Map.Entry<String, TarifficationPerson> entry : newMap.entrySet()) {
            String key = entry.getKey();
            if (historyMap.containsKey(key)) {
                TarifficationPerson newPerson = entry.getValue();
                TarifficationPerson oldPerson = historyMap.get(key); // ← Исправлено!

                // Сравниваем поля load и groupLoad
                boolean loadChanged = !Objects.equals(newPerson.getLoad(), oldPerson.getLoad());
                boolean groupLoadChanged = !Objects.equals(
                        newPerson.getGroupLoad() != null ? newPerson.getGroupLoad() : 0,
                        oldPerson.getGroupLoad() != null ? oldPerson.getGroupLoad() : 0
                );

                if (loadChanged || groupLoadChanged) {
                    // Запись изменена
                    changes.add(createHistoryRecord(newPerson,
                            TarifficationChanges.ChangeType.MODIFIED));
                }
            }
        }
    }

    // Вспомогательные методы
    private Map<String, TarifficationPerson> createPersonMap(List<TarifficationPerson> persons) {
        Map<String, TarifficationPerson> map = new HashMap<>();
        for (TarifficationPerson person : persons) {
            map.put(createKey(person), person);
        }
        return map;
    }


    private String createKey(TarifficationPerson person) {
        return createKey(person.getFioTeacher(), person.getNumberSchoolBuilding(),
                person.getSubjectName(), person.getClassName(), person.getGroupName());
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