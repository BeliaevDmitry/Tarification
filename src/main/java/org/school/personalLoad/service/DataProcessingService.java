package org.school.personalLoad.service;


import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
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

    private void processSingleMatch(List<TarifficationPerson> list, TarifficationPerson match) {
        String nameSubject = match.getSubjectName();
        String className = match.getClassName();
        Integer groupLoad = match.getLoad() / 2;

        TarifficationPerson secondGroup = new TarifficationPerson(match);

        match.setGroupName(nameSubject + " " + className + " ГР-1");
        match.setGroupLoad(groupLoad);

        secondGroup.setGroupName(nameSubject + " " + className + " ГР-2");
        secondGroup.setGroupLoad(groupLoad);

        removeByFields(list, nameSubject, className);
        list.add(match);
        list.add(secondGroup);
    }

    private void processDoubleMatch(List<TarifficationPerson> list, List<TarifficationPerson> matches) {
        if ((matches.get(0).getGroupName() == null || matches.get(0).getGroupName().isEmpty()) &&
                (matches.get(1).getGroupName() == null || matches.get(1).getGroupName().isEmpty())) {

            String nameSubject = matches.get(0).getSubjectName();
            String className = matches.get(0).getClassName();

            matches.get(0).setGroupName(nameSubject + " " + className + " ГР-1");
            matches.get(1).setGroupName(nameSubject + " " + className + " ГР-2");

            removeByFields(list, nameSubject, className);
            list.add(matches.get(0));
            list.add(matches.get(1));
        }
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

    public List<TarifficationPerson> findAllByFields(List<TarifficationPerson> list, String subject, String className) {
        return list.stream()
                .filter(person -> person.getSubjectName().equals(subject) && person.getClassName().equals(className))
                .collect(Collectors.toList());
    }

    public void sortByFIO(List<TarifficationPerson> list) {
        list.sort(Comparator.comparing(TarifficationPerson::getFioTeacher));
    }
}