package org.example.model;

import lombok.Data;

// Отдельный класс для хранения данных о предметах с группами
@Data
public class SubjectWithGroup {
    private String subjectName; //название предмета
    private String className; // название класса
    private String groupName; // название группы
    String numberSchoolBuilding; // номер корпуса
    public SubjectWithGroup(String subjectName, String className, String numberSchoolBuilding) {
        this.subjectName = subjectName;
        this.className = className;
        this.numberSchoolBuilding = numberSchoolBuilding;
    }

    // getters and toString()
}