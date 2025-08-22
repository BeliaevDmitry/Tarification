package org.example.model;

import lombok.Getter;

@Getter
public class TarifficationPerson {
    // Геттеры
    String fioTeacher;
    String numberSchoolBuilding;
    String subject;
    String classLoad;
    Integer load;
    String groupLoad;

    public TarifficationPerson(String fioTeacher, String numberSchoolBuilding, String subject, String classLoad, Integer load) {
        this.fioTeacher = fioTeacher;
        this.numberSchoolBuilding = numberSchoolBuilding;
        this.subject = subject;
        this.classLoad = classLoad;
        this.load = load;
    }

}