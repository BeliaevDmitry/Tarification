package org.school.personalLoad.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import javax.persistence.*;

@Data
@AllArgsConstructor
@Entity
@Table(name = "\"tariffication_person\"") // ← Добавьте кавычки
public class TarifficationPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // ← ДОБАВЬТЕ ID поле!

    private String fioTeacher;
    private String numberSchoolBuilding;
    private String subjectName;
    private String className;
    private Integer load;
    private String groupName;
    private Integer groupLoad;

    public TarifficationPerson() {
        // Пустой конструктор обязателен для Hibernate!
    }

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
        this.groupLoad = load;
        this.groupName = "";
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