package org.school.personalLoad.service.impl;

import org.springframework.stereotype.Service;

import org.school.personalLoad.dao.TarifficationPersonDAO;
import org.school.personalLoad.dao.TarifficationChangesDAO;
import org.school.personalLoad.dao.NamingMeshDAO;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.model.NamingMesh;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.config.AppConfig;
import org.school.personalLoad.service.DatabaseService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DatabaseServiceImpl implements DatabaseService {

    private final TarifficationPersonDAO currentDAO;
    private final TarifficationChangesDAO changesDAO;
    private final NamingMeshDAO namingMeshDAO;
    private final AppConfig appConfig;

    public DatabaseServiceImpl(AppConfig appConfig) {
        this.currentDAO = new TarifficationPersonDAO();
        this.changesDAO = new TarifficationChangesDAO();
        this.namingMeshDAO = new NamingMeshDAO();
        this.appConfig = appConfig;

        if (appConfig.isClearHistoryOnStart()) {
            log.info("История очищена по запросу (clear-history-on-start = true)");
            fullReset();
        }
    }

    /**
     * Основной метод: сравнивает и сохраняет данные
     */
    public void compareAndSave(List<TarifficationPerson> newTariffication, List<NamingMesh> namingMeshes) {
        log.info("Начало сравнения и сохранения данных...");

        // 1. Сохраняем naming mesh если предоставлен
        if (namingMeshes != null && !namingMeshes.isEmpty()) {
            namingMeshDAO.saveAll(namingMeshes);
            log.info("Сохранено записей в naming mesh: {}", namingMeshes.size());
        }

        // 2. Сначала сравниваем с предыдущей версией
        List<TarifficationChanges> changes = compareWithHistory(newTariffication);

        // 3. Сохраняем изменения в историю (если включено)
        if (!changes.isEmpty() && appConfig.isKeepHistory()) {
            changesDAO.saveAll(changes);
            log.info("Изменения тарификации сохранены в историю и содержат: {} записей", changes.size());
        }

        // 4. Сохраняем новую версию тарификации
        saveCurrentTariffication(newTariffication);

        // 5. Обновляем связи с naming mesh
        currentDAO.updateAllNamingMeshRelations();

        log.info("Новая тарификация сохранена в базу данных");
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

        log.info("Начинаем сравнение: в БД {} записей, в новой тарификации {}", oldTariffications.size(), newTariffication.size());

        if (oldTariffications.isEmpty()) {
            log.info("Первая загрузка: {} записей, история пуста", newTariffication.size());
        } else {
            findChangesComparedToHistory(oldTariffications, newTariffication, allChanges);
            log.info("Найдено изменений: {}", allChanges.size());
        }

        return allChanges;
    }

    // Дополнительные методы для работы с историей
    public List<TarifficationChanges> getAllHistory() {
        return changesDAO.findAll();
    }

    public void saveCurrentTariffication(List<TarifficationPerson> tarifficationList) {
        currentDAO.saveAll(tarifficationList);
        log.info("Сохранено записей в базу данных: {}", tarifficationList.size());
    }

    public void fullReset() {
        changesDAO.deleteAllHistory();
        currentDAO.deleteAll();
        namingMeshDAO.deleteAll();
        log.info("Полный сброс: история, текущая тарификация и naming mesh очищены");
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
        log.info("Сохранено записей в naming mesh: {}", namingMeshes.size());

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
        log.info("Обновлены связи с naming mesh");
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
                    log.debug("Обнаружено изменение для ключа: {}", key);
                    log.debug("Старое load: {}, groupLoad: {}", oldPerson.getLoad(), oldPerson.getGroupLoad());
                    log.debug("Новое load: {}, groupLoad: {}", newPerson.getLoad(), newPerson.getGroupLoad());

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