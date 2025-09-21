package org.school.personalLoad.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.persistence.*;

@Data
@AllArgsConstructor
@Entity
@Table(name = "\"tariffication_person\"")
public class TarifficationPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fioTeacher;
    private String numberSchoolBuilding;
    private String subjectName;
    private String className;
    private Integer load;
    private String groupNameEducationalPlan;
    private Integer groupLoad;

    // Связь с таблицей naming_mesh
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "subjectName", referencedColumnName = "subjectName", insertable = false, updatable = false),
            @JoinColumn(name = "className", referencedColumnName = "className", insertable = false, updatable = false),
            @JoinColumn(name = "groupNameEducationalPlan", referencedColumnName = "groupNameEducationalPlan", insertable = false, updatable = false)
    })
    private NamingMesh namingMesh;

    // Транзиентные поля (не сохраняются в БД)
    @Transient
    private String groupNameMesh;

    @Transient
    private String classNameMesh;

    public TarifficationPerson() {
        // Пустой конструктор
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
        this.groupNameEducationalPlan = "";
    }

    public TarifficationPerson(TarifficationPerson other) {
        this.fioTeacher = other.fioTeacher;
        this.numberSchoolBuilding = other.numberSchoolBuilding;
        this.subjectName = other.subjectName;
        this.className = other.className;
        this.load = other.load;
        this.groupNameEducationalPlan = other.groupNameEducationalPlan != null ? other.groupNameEducationalPlan : "";
        this.groupLoad = other.groupLoad != null ? other.groupLoad : 0;
        this.namingMesh = other.namingMesh;
    }

    // Геттеры для mesh полей
    public String getGroupNameMesh() {
        return namingMesh != null ? namingMesh.getGroupNameMesh() : "";
    }

    public String getClassNameMesh() {
        return namingMesh != null ? namingMesh.getClassNameMesh() : "";
    }
}