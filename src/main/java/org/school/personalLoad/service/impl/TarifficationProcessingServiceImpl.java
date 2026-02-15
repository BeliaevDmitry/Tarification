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

    @Override
    public List<TarifficationPerson> addingGroup(List<TarifficationPerson> list,
                                                 List<SubjectWithGroup> groupList) {
        List<TarifficationPerson> result = new ArrayList<>(list);

        // Группируем преподавателей по предметам+классам+корпусам
        Map<String, List<TarifficationPerson>> groupedBySubjectClass = new HashMap<>();

        for (TarifficationPerson person : list) {
            String key = createGroupKey(person.getSubjectName(),
                    person.getClassName(),
                    person.getNumberSchoolBuilding());
            groupedBySubjectClass
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(person);
        }

        // Обрабатываем каждый предмет+класс
        for (SubjectWithGroup group : groupList) {
            String key = createGroupKey(group.getSubjectName(),
                    group.getClassName(),
                    group.getNumberSchoolBuilding());

            List<TarifficationPerson> teachers = groupedBySubjectClass.get(key);
            if (teachers == null || teachers.isEmpty()) {
                continue;
            }

            // Получаем исторические данные
            List<TarifficationPerson> historicalMatches =
                    databaseService.findAllByFieldsHistory(group.getSubjectName(),
                            group.getClassName(), group.getNumberSchoolBuilding());

            // Обрабатываем в зависимости от количества преподавателей
            if (teachers.size() == 1) {
                processSingleTeacher(result, teachers.get(0), historicalMatches, group);
            } else if (teachers.size() == 2) {
                processTwoTeachers(result, teachers, historicalMatches, group);
            } else {
                System.out.println("⚠️ Неожиданное количество преподавателей (" +
                        teachers.size() + ") для " + key);
            }
        }

        return result;
    }

    private void processSingleTeacher(List<TarifficationPerson> result,
                                      TarifficationPerson teacher,
                                      List<TarifficationPerson> historicalMatches,
                                      SubjectWithGroup group) {

        String subject = teacher.getSubjectName();
        String className = teacher.getClassName();
        String building = teacher.getNumberSchoolBuilding();
        Integer totalLoad = teacher.getLoad();

        System.out.println("👨‍🏫 Один преподаватель: " + teacher.getFioTeacher() +
                " - " + subject + " " + className +
                " (нагрузка: " + totalLoad + " ч)");

        // Делим нагрузку пополам между подгруппами
        Integer groupLoad = totalLoad / 2;

        createSubgroups(result, teacher, subject, className, building, groupLoad);
    }

    private void processTwoTeachers(List<TarifficationPerson> result,
                                    List<TarifficationPerson> teachers,
                                    List<TarifficationPerson> historicalMatches,
                                    SubjectWithGroup group) {

        if (teachers.size() != 2) return;

        String subject = teachers.get(0).getSubjectName();
        String className = teachers.get(0).getClassName();
        String building = teachers.get(0).getNumberSchoolBuilding();

        System.out.println("👨‍🏫👩‍🏫 Два преподавателя: " +
                teachers.get(0).getFioTeacher() + " и " +
                teachers.get(1).getFioTeacher() +
                " - " + subject + " " + className);

        // Каждый преподаватель ведет свою подгруппу
        // Первый преподаватель - первая подгруппа
        TarifficationPerson firstGroup = new TarifficationPerson(teachers.get(0));
        firstGroup.setGroupNameEducationalPlan(formatGroupNameBase(subject, className) + " 1 гр");
        firstGroup.setGroupLoad(teachers.get(0).getLoad());

        // Второй преподаватель - вторая подгруппа
        TarifficationPerson secondGroup = new TarifficationPerson(teachers.get(1));
        secondGroup.setGroupNameEducationalPlan(formatGroupNameBase(subject, className) + " 2 гр");
        secondGroup.setGroupLoad(teachers.get(1).getLoad());

        // Удаляем старые записи
        removeByFields(result, subject, className, building);

        // Добавляем новые записи с подгруппами
        result.add(firstGroup);
        result.add(secondGroup);
    }

    private void createSubgroups(List<TarifficationPerson> result,
                                 TarifficationPerson teacher,
                                 String subject, String className,
                                 String building, Integer groupLoad) {

        String groupNameBase = formatGroupNameBase(subject, className);

        // Первая подгруппа
        TarifficationPerson firstGroup = new TarifficationPerson(teacher);
        firstGroup.setGroupNameEducationalPlan(groupNameBase + " 1 гр");
        firstGroup.setGroupLoad(groupLoad);

        // Вторая подгруппа
        TarifficationPerson secondGroup = new TarifficationPerson(teacher);
        secondGroup.setGroupNameEducationalPlan(groupNameBase + " 2 гр");
        secondGroup.setGroupLoad(groupLoad);

        // Удаляем старую запись
        removeByFields(result, subject, className, building);

        // Добавляем новые записи с подгруппами
        result.add(firstGroup);
        result.add(secondGroup);
    }

    private String createGroupKey(String subject, String className, String building) {
        return building + "|" + subject + "|" + className;
    }

    private void removeByFields(List<TarifficationPerson> list,
                                String subject, String className, String building) {
        Iterator<TarifficationPerson> iterator = list.iterator();
        while (iterator.hasNext()) {
            TarifficationPerson person = iterator.next();
            if (person.getSubjectName().equals(subject) &&
                    person.getClassName().equals(className) &&
                    person.getNumberSchoolBuilding().equals(building)) {
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

    @Override
    public void sortByFIO(List<TarifficationPerson> list) {
        list.sort(Comparator.comparing(TarifficationPerson::getFioTeacher));
    }

    @Override
    public void sortHistoryByDate(List<TarifficationChanges> historyList) {
        historyList.sort(Comparator.comparing(TarifficationChanges::getChangeDate));
    }
}