package org.school.personalLoad.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "\"tariffication_changes_mesh\"") // Название таблицы в БД
@Data
@NoArgsConstructor
public class TarifficationChangesMesh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Связь с основной сущностью изменения (опционально, но часто полезно)
    private Long tarifficationChangeId;

    // Основные данные, которые нас интересуют для этого контекста
    private String fioTeacher;
    private String subjectName;
    private String className;

    // Название группы по учебному плану (было/стало)
    private String oldGroupNameEducationalPlan;
    private String newGroupNameEducationalPlan;

    // Название группы МЭШ (было/стало) - это ключевое поле для этой таблицы
    private String oldGroupNameMesh;
    private String newGroupNameMesh;

    // Нагрузка группы (может пригодиться для контекста)
    private Integer groupLoad;

    // Тип произошедшего изменения с названием МЭШ
    @Enumerated(EnumType.STRING)
    private MeshChangeType meshChangeType;

    // Дата и время изменения
    @Column(updatable = false)
    private LocalDateTime changeDate;

    // Enum для типов изменений specifically для МЭШ
    public enum MeshChangeType {
        MESH_NAME_ADDED("Добавлено название МЭШ"),
        MESH_NAME_REMOVED("Удалено название МЭШ"),
        MESH_NAME_MODIFIED("Изменено название МЭШ"),
        MESH_MAPPING_ADDED("Добавлена связь УП-МЭШ"),
        MESH_MAPPING_REMOVED("Удалена связь УП-МЭШ"),
        MESH_MAPPING_MODIFIED("Изменена связь УП-МЭШ"); // Например, была привязана одна группа МЭШ, стала другая

        private final String russianDescription;

        MeshChangeType(String russianDescription) {
            this.russianDescription = russianDescription;
        }

        public String getRussianDescription() {
            return russianDescription;
        }

        @Override
        public String toString() {
            return russianDescription;
        }
    }

    /**
     * Конструктор для удобного создания записи об изменении.
     * Это лишь один из возможных вариантов.
     */
    public TarifficationChangesMesh(Long tarifficationChangeId,
                                    String fioTeacher,
                                    String subjectName,
                                    String className,
                                    String oldGroupNameEducationalPlan,
                                    String newGroupNameEducationalPlan,
                                    String oldGroupNameMesh,
                                    String newGroupNameMesh,
                                    Integer groupLoad,
                                    MeshChangeType meshChangeType) {
        this.tarifficationChangeId = tarifficationChangeId;
        this.fioTeacher = fioTeacher;
        this.subjectName = subjectName;
        this.className = className;
        this.oldGroupNameEducationalPlan = oldGroupNameEducationalPlan;
        this.newGroupNameEducationalPlan = newGroupNameEducationalPlan;
        this.oldGroupNameMesh = oldGroupNameMesh;
        this.newGroupNameMesh = newGroupNameMesh;
        this.groupLoad = groupLoad;
        this.meshChangeType = meshChangeType;
        this.changeDate = LocalDateTime.now(); // Дата проставляется автоматически при создании
    }

    /**
     * Метод для получения русского описания типа изменения МЭШ.
     */
    public String getMeshChangeTypeRussian() {
        return meshChangeType != null ? meshChangeType.getRussianDescription() : "";
    }

    /**
     * Метод для получения краткого описания изменения (например, для лога или отчета).
     * Формат: "СтароеНазвание -> НовоеНазвание"
     */
    public String getChangeSummary() {
        if (oldGroupNameMesh != null && newGroupNameMesh != null) {
            return oldGroupNameMesh + " -> " + newGroupNameMesh;
        } else if (oldGroupNameMesh == null && newGroupNameMesh != null) {
            return "[Нет] -> " + newGroupNameMesh;
        } else if (oldGroupNameMesh != null && newGroupNameMesh == null) {
            return oldGroupNameMesh + " -> [Нет]";
        } else {
            return "[Неизвестно]";
        }
    }
}