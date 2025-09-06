package org.school.personalLoad.service;

import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.*;
import java.util.stream.Collectors;

public class DataProcessingService {
    private final DatabaseService databaseService; // ← Добавляем поле

    // Конструктор с зависимостью
    public DataProcessingService(DatabaseService databaseService) {
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

        if (historicalMatches.get(0).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {
            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(0));
            result.add(historicalMatches.get(1));
        } else if (match.getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())) {
            TarifficationPerson secondGroup = new TarifficationPerson(match);
            String groupNameBase = formatGroupNameBase(nameSubject, className);
            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(0));
            if (extractGroupNumber(historicalMatches.get(0).getGroupName()) == 1) {
                secondGroup.setGroupName(groupNameBase + " 2 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            } else if (extractGroupNumber(historicalMatches.get(0).getGroupName()) == 2) {
                secondGroup.setGroupName(groupNameBase + " 1 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            }
        } else if (match.getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {
            TarifficationPerson secondGroup = new TarifficationPerson(match);
            String groupNameBase = formatGroupNameBase(nameSubject, className);
            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(1));
            if (extractGroupNumber(historicalMatches.get(1).getGroupName()) == 1) {
                secondGroup.setGroupName(groupNameBase + " 2 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            } else if (extractGroupNumber(historicalMatches.get(1).getGroupName()) == 2) {
                secondGroup.setGroupName(groupNameBase + " 1 гр");
                secondGroup.setGroupLoad(groupLoad);
                result.add(secondGroup);
            }
        } else {

            TarifficationPerson secondGroup = new TarifficationPerson(match);
            String groupNameBase = formatGroupNameBase(nameSubject, className);

            // Изменяем оригинальную запись
            match.setGroupName(groupNameBase + " 1 гр");
            match.setGroupLoad(groupLoad);

            // 4. Изменяем копию
            secondGroup.setGroupName(groupNameBase + " 2 гр");
            secondGroup.setGroupLoad(groupLoad);

            // 5. Добавляем обе записи обратно в список
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
        if (matches.size() < 2) return;

        String nameSubject = matches.get(0).getSubjectName();
        String className = matches.get(0).getClassName();
        String groupNameBase = formatGroupNameBase(nameSubject, className);


        if ((matches.get(0).getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())
                || matches.get(0).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher()))
                &&
                (matches.get(1).getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())
                || matches.get(1).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher()))) {

            removeByFields(result, nameSubject, className);
            result.add(historicalMatches.get(0));
            result.add(historicalMatches.get(1));

        } else if (matches.get(0).getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())
                || matches.get(0).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {

            TarifficationPerson secondGroup = new TarifficationPerson(matches.get(0));
            removeByFields(result, nameSubject, className);
            if (matches.get(0).getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())) {
                result.add(historicalMatches.get(0));
                if (extractGroupNumber(historicalMatches.get(0).getGroupName()) == 1) {
                    secondGroup.setGroupName(groupNameBase + " 2 гр");
                    result.add(secondGroup);
                } else if (extractGroupNumber(historicalMatches.get(0).getGroupName()) == 2) {
                    secondGroup.setGroupName(groupNameBase + " 1 гр");
                    result.add(secondGroup);
                }
            } else if (matches.get(0).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {
                result.add(historicalMatches.get(0));
                if (extractGroupNumber(historicalMatches.get(1).getGroupName()) == 1) {
                    secondGroup.setGroupName(groupNameBase + " 2 гр");
                    result.add(secondGroup);
                } else if (extractGroupNumber(historicalMatches.get(1).getGroupName()) == 2) {
                    secondGroup.setGroupName(groupNameBase + " 1 гр");
                    result.add(secondGroup);
                }
            } else {
                System.out.println("ошибка в группах");
            }
        } else if (matches.get(1).getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())
                || matches.get(1).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {

            TarifficationPerson secondGroup = new TarifficationPerson(matches.get(0));
            removeByFields(result, nameSubject, className);
            if (matches.get(1).getFioTeacher().equals(historicalMatches.get(0).getFioTeacher())) {
                result.add(historicalMatches.get(0));
                if (extractGroupNumber(historicalMatches.get(0).getGroupName()) == 1) {
                    secondGroup.setGroupName(groupNameBase + " 2 гр");
                    result.add(secondGroup);
                } else if (extractGroupNumber(historicalMatches.get(0).getGroupName()) == 2) {
                    secondGroup.setGroupName(groupNameBase + " 1 гр");
                    result.add(secondGroup);
                }
            } else if (matches.get(1).getFioTeacher().equals(historicalMatches.get(1).getFioTeacher())) {
                result.add(historicalMatches.get(0));
                if (extractGroupNumber(historicalMatches.get(1).getGroupName()) == 1) {
                    secondGroup.setGroupName(groupNameBase + " 2 гр");
                    result.add(secondGroup);
                } else if (extractGroupNumber(historicalMatches.get(1).getGroupName()) == 2) {
                    secondGroup.setGroupName(groupNameBase + " 1 гр");
                    result.add(secondGroup);
                }
            } else {

                matches.get(0).setGroupName(groupNameBase + " 1 гр");
                matches.get(1).setGroupName(groupNameBase + " 2 гр");

                // Обновляем записи в результате
                removeByFields(result, nameSubject, className);
                result.add(matches.get(0));
                result.add(matches.get(1));
            }
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
        String cleanedSubjectName = subjectName.replaceAll("\\s*(ООО|НОО|СОО)\\s*", "").trim();
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