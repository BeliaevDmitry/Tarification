package org.school.personalLoad.service;

import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.*;
import java.util.stream.Collectors;

public class DataProcessingService {

    public List<TarifficationPerson> addingGroup(List<TarifficationPerson> list, List<SubjectWithGroup> groupList) {
        for (SubjectWithGroup group : groupList) {
            List<TarifficationPerson> listMatches = findAllByFields(list, group.getSubjectName(), group.getClassName());

            if (listMatches.size() == 1) {
                processSingleMatch(list, listMatches.get(0));
            } else if (listMatches.size() == 2) {
                processDoubleMatch(list, listMatches);
            }
        }
        return list;
    }

    private void processSingleMatch(List<TarifficationPerson> result, TarifficationPerson match) {
        String nameSubject = match.getSubjectName();
        String className = match.getClassName();
        Integer groupLoad = match.getLoad() / 2;


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

    public void removeByFields(List<TarifficationPerson> list, String targetSubject, String targetClass) {
        Iterator<TarifficationPerson> iterator = list.iterator();
        while (iterator.hasNext()) {
            TarifficationPerson person = iterator.next();
            if (person.getSubjectName().equals(targetSubject) && person.getClassName().equals(targetClass)) {
                iterator.remove();
            }
        }
    }

    private void processDoubleMatch(List<TarifficationPerson> result, List<TarifficationPerson> matches) {
        if (matches.size() < 2) return;

        String nameSubject = matches.get(0).getSubjectName();
        String className = matches.get(0).getClassName();
        String groupNameBase = formatGroupNameBase(nameSubject, className);

        matches.get(0).setGroupName(groupNameBase + " 1 гр");
        matches.get(1).setGroupName(groupNameBase + " 2 гр");

        // Обновляем записи в результате
        removeByFields(result, nameSubject, className);
        result.add(matches.get(0));
        result.add(matches.get(1));
    }

    private String formatGroupNameBase(String subjectName, String className) {
        String cleanedSubjectName = subjectName.replaceAll("\\s*(ООО|НОО|СОО)\\s*", "").trim();
        String formattedClassName = className.replaceAll("[\\s-]+", "");
        return cleanedSubjectName + " " + className + " " + formattedClassName;
    }

    public void sortByFIO(List<TarifficationPerson> list) {
        list.sort(Comparator.comparing(TarifficationPerson::getFioTeacher));
    }

    public void sortHistoryByDate(List<TarifficationChanges> historyList) {
        historyList.sort(Comparator.comparing(TarifficationChanges::getChangeDate));
    }

    public List<TarifficationPerson> findAllByFields(List<TarifficationPerson> list, String subject, String className) {
        return list.stream()
                .filter(person -> person.getSubjectName().equals(subject) && person.getClassName().equals(className))
                .collect(Collectors.toList());
    }
}