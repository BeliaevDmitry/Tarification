package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "\"tariffication_changes\"")
@Data
public class TarifficationChanges {
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
    private String groupNameMesh;

    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    @Column(updatable = false)
    private LocalDateTime changeDate;

    public enum ChangeType {
        ADDED("ДОБАВЛЕНО"),
        REMOVED("УДАЛЕНО"),
        MODIFIED("ИЗМЕНЕНО"),
        MESH_MAPPING_CHANGED("ИЗМЕНЕНИЕ СВЯЗИ УП С МЭШ");

        private final String russianName;

        ChangeType(String russianName) {
            this.russianName = russianName;
        }

        public String getRussianName() {
            return russianName;
        }

        @Override
        public String toString() {
            return russianName;
        }
    }

    /**
     * Метод для получения русского названия типа изменения
     */
    public String getChangeTypeRussian() {
        return changeType != null ? changeType.getRussianName() : "";
    }

    /**
     * Метод для получения описания изменения (используется для naming mesh)
     */
    public String getChangeDescription() {
        return fioTeacher != null ? fioTeacher : "";
    }
}