package org.school.personalLoad.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TarifficationPerson {
    String fioTeacher;
    String numberSchoolBuilding;
    String subjectName;
    String className;
    Integer load;
    String groupName;
    Integer groupLoad;

    public TarifficationPerson(String fioTeacher,
                               String numberSchoolBuilding,
                               String subjectName,
                               String className,
                               Integer load) {
        this.fioTeacher = fioTeacher;
        this.numberSchoolBuilding = numberSchoolBuilding;
        this.subjectName = subjectName;
        this.className = className;
        this.load = load;
        this.groupLoad = load; // ← Инициализируем стандартным значением
        this.groupName = ""; // ← Инициализируем пустой строкой
    }

    public TarifficationPerson(TarifficationPerson other) {
        this.fioTeacher = other.fioTeacher;
        this.numberSchoolBuilding = other.numberSchoolBuilding;
        this.subjectName = other.subjectName;
        this.className = other.className;
        this.load = other.load;
        this.groupName = other.groupName != null ? other.groupName : "";
        this.groupLoad = other.groupLoad != null ? other.groupLoad : 0;
    }
}