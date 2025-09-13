package org.school.personalLoad.service.impl;

import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.TarifficationProcessingService;
import org.school.personalLoad.service.DatabaseService;

import java.util.*;
import java.util.stream.Collectors;

public class TarifficationProcessingServiceImpl implements TarifficationProcessingService {
    private final DatabaseService databaseService; // ← Добавляем поле

    // Конструктор с зависимостью
    public TarifficationProcessingServiceImpl(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public List<TarifficationPerson> addingGroup(List<TarifficationPerson> list,
                                                 List<SubjectWithGroup> groupList) {
        for (SubjectWithGroup group : groupList) {
            List<TarifficationPerson> listMatches = findAllByFields(list, group.getSubjectName(),
                    group.getClassName(), group.getNumberSchoolBuilding());

            List<TarifficationPerson> listMatchesInTariffication =
                    databaseService.findAllByFieldsHistory(group.getSubjectName(),
                            group.getClassName(), group.getNumberSchoolBuilding());

            if (listMatches.size() == 1) {
                processSingleMatch(list, listMatches.get(0), listMatchesInTariffication);
            } else if (listMatches.size() == 2) {
                processDoubleMatch(list, listMatches, listMatchesInTariffication);
            }
        }
        return list;
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
        Integer groupLoad = match.getLoad() / 2;

        // Проверка на null и пустой список historicalMatches
        if (historicalMatches == null || historicalMatches.isEmpty()) {
            TarifficationPerson secondGroup = new TarifficationPerson(match);
            String groupNameBase = formatGroupNameBase(nameSubject, className);

            // Изменяем оригинальную запись
            match.setGroupNameEducationalPlan(groupNameBase + " 1 гр");
            match.setGroupLoad(groupLoad);

            // Изменяем копию
            secondGroup.setGroupNameEducationalPlan(groupNameBase + " 2 гр");
            secondGroup.setGroupLoad(groupLoad);

            // Добавляем обе записи обратно в список
            removeByFields(result, nameSubject, className);
            result.add(match);
            result.add(secondGroup);
            return;
        }

        // Оригинальная логика для не-null historicalMatches
        if (historicalMatches.get(0).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {
            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(0));
            result.add(historicalMatches.get(1));
        } else if (match.getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())) {
            TarifficationPerson secondGroup = new TarifficationPerson(match);
            String groupNameBase = formatGroupNameBase(nameSubject, className);
            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(0));
            if (extractGroupNumber(historicalMatches.get(0).getGroupNameEducationalPlan()) == 1) {
                secondGroup.setGroupNameEducationalPlan(groupNameBase + " 2 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            } else if (extractGroupNumber(historicalMatches.get(0).getGroupNameEducationalPlan()) == 2) {
                secondGroup.setGroupNameEducationalPlan(groupNameBase + " 1 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            }
        } else if (match.getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {
            TarifficationPerson secondGroup = new TarifficationPerson(match);
            String groupNameBase = formatGroupNameBase(nameSubject, className);
            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(1));
            if (extractGroupNumber(historicalMatches.get(1).getGroupNameEducationalPlan()) == 1) {
                secondGroup.setGroupNameEducationalPlan(groupNameBase + " 2 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            } else if (extractGroupNumber(historicalMatches.get(1).getGroupNameEducationalPlan()) == 2) {
                secondGroup.setGroupNameEducationalPlan(groupNameBase + " 1 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            }
        } else {
            TarifficationPerson secondGroup = new TarifficationPerson(match);
            String groupNameBase = formatGroupNameBase(nameSubject, className);

            // Изменяем оригинальную запись
            match.setGroupNameEducationalPlan(groupNameBase + " 1 гр");
            match.setGroupLoad(groupLoad);

            // Изменяем копию
            secondGroup.setGroupNameEducationalPlan(groupNameBase + " 2 гр");
            secondGroup.setGroupLoad(groupLoad);

            // Добавляем обе записи обратно в список
            removeByFields(result, nameSubject, className);
            result.add(match);
            result.add(secondGroup);
        }
    }

    private Integer extractGroupNumber(String groupName) {
        if (groupName == null) return null;
        if (groupName.contains("1 гр") || groupName.contains("1гр")) return 1;
        if (groupName.contains("2 гр") || groupName.contains("2гр")) return 2;
        return null;
    }

    private void processDoubleMatch(List<TarifficationPerson> result,
                                    List<TarifficationPerson> matches,
                                    List<TarifficationPerson> historicalMatches) {

        // Проверка на минимальное количество matches
        if (matches.size() < 2) return;

        // Проверка на наличие исторических данных. Если их нет, обрабатываем как новый случай.
        if (historicalMatches == null || historicalMatches.isEmpty()) {
            String nameSubject = matches.get(0).getSubjectName();
            String className = matches.get(0).getClassName();
            String groupNameBase = formatGroupNameBase(nameSubject, className);

            matches.get(0).setGroupNameEducationalPlan(groupNameBase + " 1 гр");
            matches.get(1).setGroupNameEducationalPlan(groupNameBase + " 2 гр");

            removeByFields(result, nameSubject, className);
            result.add(matches.get(0));
            result.add(matches.get(1));
            return;
        }

        // Основная логика, если historicalMatches не пуст
        // (по условию, если не пуст, то size == 2)
        String nameSubject = matches.get(0).getSubjectName();
        String className = matches.get(0).getClassName();
        String groupNameBase = formatGroupNameBase(nameSubject, className);

        boolean bothTeachersMatchHistory =
                (historicalMatches.get(0).getFioTeacher().equals(matches.get(0).getFioTeacher())
                        || historicalMatches.get(1).getFioTeacher().equals(matches.get(0).getFioTeacher()))
                        &&
                        (historicalMatches.get(0).getFioTeacher().equals(matches.get(1).getFioTeacher())
                                || historicalMatches.get(1).getFioTeacher().equals(matches.get(1).getFioTeacher()));

        boolean firstTeacherMatches = historicalMatches.get(0).getFioTeacher().equals(matches.get(0).getFioTeacher())
                || historicalMatches.get(1).getFioTeacher().equals(matches.get(0).getFioTeacher());

        boolean secondTeacherMatches = historicalMatches.get(0).getFioTeacher().equals(matches.get(1).getFioTeacher())
                || historicalMatches.get(1).getFioTeacher().equals(matches.get(1).getFioTeacher());


        if (bothTeachersMatchHistory) {
            // Случай 1: Оба текущих преподавателя совпадают с историческими
            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(0));
            result.add(historicalMatches.get(1));

        } else if (firstTeacherMatches) {
            // Случай 2: Совпадает только первый преподаватель из matches
            processSingleTeacherMatch(result, matches, historicalMatches, nameSubject, className, groupNameBase, 0);

        } else if (secondTeacherMatches) {
            // Случай 3: Совпадает только второй преподаватель из matches
            processSingleTeacherMatch(result, matches, historicalMatches, nameSubject, className, groupNameBase, 1);

        } else {
            // Случай 4: Не совпадает ни один преподаватель
            matches.get(0).setGroupNameEducationalPlan(groupNameBase + " 1 гр");
            matches.get(1).setGroupNameEducationalPlan(groupNameBase + " 2 гр");

            removeByFields(result, nameSubject, className);
            result.add(matches.get(0));
            result.add(matches.get(1));
        }
    }

    /**
     * Вспомогательный метод для обработки случая, когда совпадает только один преподаватель.
     *
     * @param matchIndex индекс совпавшего преподавателя в списке matches (0 или 1)
     */
    private void processSingleTeacherMatch(List<TarifficationPerson> result,
                                           List<TarifficationPerson> matches,
                                           List<TarifficationPerson> historicalMatches,
                                           String nameSubject,
                                           String className,
                                           String groupNameBase,
                                           int matchIndex) {

        // Индекс другого преподавателя (не совпавшего)
        int otherMatchIndex = (matchIndex == 0) ? 1 : 0;

        // Определяем, какой именно исторический преподаватель совпал
        TarifficationPerson historicalMatch;
        TarifficationPerson otherHistorical;

        if (matches.get(matchIndex).getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())) {
            historicalMatch = historicalMatches.get(0);
            otherHistorical = historicalMatches.get(1);
        } else {
            historicalMatch = historicalMatches.get(1);
            otherHistorical = historicalMatches.get(0);
        }

        // Создаем запись для второй группы на основе актуальных данных
        TarifficationPerson secondGroup = new TarifficationPerson(matches.get(otherMatchIndex));

        removeByFields(result, nameSubject, className);
        result.add(historicalMatch); // Добавляем историческую запись

        // Определяем номер группы для новой записи на основе номера группы исторического преподавателя
        Integer historicalGroupNumber = extractGroupNumber(historicalMatch.getGroupNameEducationalPlan());

        if (historicalGroupNumber == 1) {
            secondGroup.setGroupNameEducationalPlan(groupNameBase + " 2 гр");
            result.add(secondGroup);
        } else if (historicalGroupNumber == 2) {
            secondGroup.setGroupNameEducationalPlan(groupNameBase + " 1 гр");
            result.add(secondGroup);
        } else {
            // Если по какой-то причине не удалось определить номер группы у исторической записи
            System.out.println("Ошибка: не удалось определить номер группы для исторической записи");
            // Добавляем обе исходные записи как есть
            result.add(matches.get(otherMatchIndex));
        }
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
        // Обрабатываем оба случая: "У ООО" и "ООО У"
        String cleanedSubjectName = subjectName
                .replaceAll("\\s*(НОО|ООО|СОО)\\s*У\\s*", "")  // Затем "ООО У"
                .replaceAll("\\s*(НОО|ООО|СОО)\\s*", "")       // Затем просто уровни
                .trim();

        String formattedClassName = className.replaceAll("[\\s-]+", "");
        return cleanedSubjectName + " " + className + " " + formattedClassName;
    }

    private List<TarifficationPerson> findAllByFields(List<TarifficationPerson> list, String subject, String className, String NumberSchoolBuilding) {
        return list.stream()
                .filter(person -> person.getSubjectName().equals(subject) && person.getClassName().equals(className)
                        && person.getNumberSchoolBuilding().equals(NumberSchoolBuilding))
                .collect(Collectors.toList());
    }
}