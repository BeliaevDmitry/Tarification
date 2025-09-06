package org.school.personalLoad.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "\"tariffication_changes\"") // ← Добавьте кавычки
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
    private String groupName;
    private Integer groupLoad;

    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    @Column(updatable = false)
    private LocalDateTime changeDate;

    public enum ChangeType {
        ADDED("ДОБАВЛЕНО"),
        REMOVED("УДАЛЕНО"),
        MODIFIED("ИЗМЕНЕНО");

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

    public String getChangeTypeRussian() {
        return changeType.getRussianName();
    }
}