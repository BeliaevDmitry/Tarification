package org.school.personalLoad.service.impl;

import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.TarifficationProcessingService;
import org.school.personalLoad.service.DatabaseService;

import java.util.*;
import java.util.stream.Collectors;

public class TarifficationProcessingServiceImpl implements TarifficationProcessingService {
    private final DatabaseService databaseService;

    public TarifficationProcessingServiceImpl(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public List<TarifficationPerson> addingGroup(List<TarifficationPerson> list,
                                                 List<SubjectWithGroup> groupList) {
        List<TarifficationPerson> result = new ArrayList<>(list);

        for (SubjectWithGroup group : groupList) {
            List<TarifficationPerson> listMatches = findAllByFields(result, group.getSubjectName(),
                    group.getClassName(), group.getNumberSchoolBuilding());

            List<TarifficationPerson> listMatchesInTariffication =
                    databaseService.findAllByFieldsHistory(group.getSubjectName(),
                            group.getClassName(), group.getNumberSchoolBuilding());

            if (listMatches.size() == 1) {
                processSingleMatch(result, listMatches.get(0), listMatchesInTariffication);
            } else if (listMatches.size() == 2) {
                processDoubleMatch(result, listMatches, listMatchesInTariffication);
            }
        }
        return result;
    }

    public void sortByFIO(List<TarifficationPerson> list) {
        list.sort(Comparator.comparing(TarifficationPerson::getFioTeacher));
    }

    public void sortHistoryByDate(List<TarifficationChanges> historyList) {
        historyList.sort(Comparator.comparing(TarifficationChanges::getChangeDate));
    }

    private void processSingleMatch(List<TarifficationPerson> result,
                                    TarifficationPerson match,
                                    List<TarifficationPerson> historicalMatches) {
        String nameSubject = match.getSubjectName();
        String className = match.getClassName();
        Integer currentLoad = match.getLoad();
        Integer groupLoad = currentLoad / 2;

        // Проверяем, изменилась ли нагрузка по сравнению с историческими данными
        if (hasLoadChanged(historicalMatches, currentLoad)) {
            // Нагрузка изменилась - создаем новые записи
            createNewGroups(result, match, nameSubject, className, groupLoad);
            return;
        }

        if (historicalMatches == null || historicalMatches.isEmpty()) {
            createNewGroups(result, match, nameSubject, className, groupLoad);
            return;
        }

        // Проверяем, совпадают ли преподаватели с историческими данными
        boolean teacherMatchesHistory = historicalMatches.stream()
                .anyMatch(hist -> hist.getFioTeacher().equals(match.getFioTeacher()));

        if (teacherMatchesHistory) {
            // Преподаватель совпадает - используем исторические данные
            removeByFields(result, nameSubject, className);
            result.addAll(historicalMatches);
        } else {
            // Преподаватель изменился - создаем новые группы
            createNewGroups(result, match, nameSubject, className, groupLoad);
        }
    }

    private void processDoubleMatch(List<TarifficationPerson> result,
                                    List<TarifficationPerson> matches,
                                    List<TarifficationPerson> historicalMatches) {

        if (matches.size() < 2) return;

        String nameSubject = matches.get(0).getSubjectName();
        String className = matches.get(0).getClassName();
        Integer currentTotalLoad = matches.stream().mapToInt(TarifficationPerson::getLoad).sum();

        // Проверяем, изменилась ли общая нагрузка
        if (hasLoadChanged(historicalMatches, currentTotalLoad)) {
            // Нагрузка изменилась - создаем новые записи
            createNewGroupsFromMultiple(result, matches, nameSubject, className);
            return;
        }

        if (historicalMatches == null || historicalMatches.isEmpty()) {
            createNewGroupsFromMultiple(result, matches, nameSubject, className);
            return;
        }

        // Проверяем совпадение преподавателей
        boolean bothTeachersMatch = historicalMatches.stream()
                .allMatch(hist -> matches.stream()
                        .anyMatch(current -> current.getFioTeacher().equals(hist.getFioTeacher())));

        if (bothTeachersMatch) {
            // Оба преподавателя совпадают - используем исторические данные
            removeByFields(result, nameSubject, className);
            result.addAll(historicalMatches);
        } else {
            // Преподаватели изменились - создаем новые группы
            createNewGroupsFromMultiple(result, matches, nameSubject, className);
        }
    }

    /**
     * Проверяет, изменилась ли нагрузка по сравнению с историческими данными
     */
    private boolean hasLoadChanged(List<TarifficationPerson> historicalMatches, Integer currentLoad) {
        if (historicalMatches == null || historicalMatches.isEmpty()) {
            return false; // Нет исторических данных для сравнения
        }

        Integer historicalTotalLoad = historicalMatches.stream()
                .mapToInt(TarifficationPerson::getLoad)
                .sum();

        return !historicalTotalLoad.equals(currentLoad);
    }

    /**
     * Создает новые группы для одного преподавателя (когда нагрузка изменилась)
     */
    private void createNewGroups(List<TarifficationPerson> result,
                                 TarifficationPerson original,
                                 String nameSubject,
                                 String className,
                                 Integer groupLoad) {
        String groupNameBase = formatGroupNameBase(nameSubject, className);

        TarifficationPerson firstGroup = new TarifficationPerson(original);
        TarifficationPerson secondGroup = new TarifficationPerson(original);

        firstGroup.setGroupNameEducationalPlan(groupNameBase + " 1 гр");
        firstGroup.setGroupLoad(groupLoad);
        secondGroup.setGroupNameEducationalPlan(groupNameBase + " 2 гр");
        secondGroup.setGroupLoad(groupLoad);

        removeByFields(result, nameSubject, className);
        result.add(firstGroup);
        result.add(secondGroup);
    }

    /**
     * Создает новые группы для двух преподавателей (когда нагрузка изменилась)
     */
    private void createNewGroupsFromMultiple(List<TarifficationPerson> result,
                                             List<TarifficationPerson> matches,
                                             String nameSubject,
                                             String className) {
        String groupNameBase = formatGroupNameBase(nameSubject, className);

        // Распределяем нагрузку поровну
        Integer groupLoad = matches.get(0).getLoad() / 2;

        matches.get(0).setGroupNameEducationalPlan(groupNameBase + " 1 гр");
        matches.get(0).setGroupLoad(groupLoad);
        matches.get(1).setGroupNameEducationalPlan(groupNameBase + " 2 гр");
        matches.get(1).setGroupLoad(groupLoad);

        removeByFields(result, nameSubject, className);
        result.add(matches.get(0));
        result.add(matches.get(1));
    }

    private Integer extractGroupNumber(String groupName) {
        if (groupName == null) return null;
        if (groupName.contains("1 гр") || groupName.contains("1гр")) return 1;
        if (groupName.contains("2 гр") || groupName.contains("2гр")) return 2;
        return null;
    }

    private void removeByFields(List<TarifficationPerson> list, String targetSubject, String targetClass) {
        Iterator<TarifficationPerson> iterator = list.iterator();
        while (iterator.hasNext()) {
            TarifficationPerson person = iterator.next();
            if (person.getSubjectName().equals(targetSubject) && person.getClassName().equals(targetClass)) {
                iterator.remove();
            }
        }
    }

    private String formatGroupNameBase(String subjectName, String className) {
        String cleanedSubjectName = subjectName
                .replaceAll("\\s*(НОО|ООО|СОО)\\s*У\\s*", "")
                .replaceAll("\\s*(НОО|ООО|СОО)\\s*", "")
                .trim();

        String formattedClassName = className.replaceAll("[\\s-]+", "");
        return cleanedSubjectName + " " + className + " " + formattedClassName;
    }

    private List<TarifficationPerson> findAllByFields(List<TarifficationPerson> list, String subject, String className, String NumberSchoolBuilding) {
        return list.stream()
                .filter(person -> person.getSubjectName().equals(subject)
                        && person.getClassName().equals(className)
                        && person.getNumberSchoolBuilding().equals(NumberSchoolBuilding))
                .collect(Collectors.toList());
    }
}