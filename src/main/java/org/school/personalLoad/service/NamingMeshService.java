package org.school.personalLoad.service;

import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.model.TarifficationChanges;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с mapping между учебным планом (УП) и МЭШ
 */
public interface NamingMeshService {

    /**
     * Обрабатывает файл Excel с mapping между УП и МЭШ
     *
     * @param filePath путь к файлу Excel
     * @return список изменений, обнаруженных при обработке
     */
    List<TarifficationChanges> processNamingMeshFile(String filePath);

    /**
     * Возвращает все записи mapping между УП и МЭШ
     *
     * @return список всех NamingMesh записей
     */
    List<NamingMesh> getAllNamingMeshes();

    /**
     * Находит запись mapping по ключевым полям
     *
     * @param subjectName название предмета
     * @param className название класса
     * @param groupNameEducationalPlan название группы по УП
     * @return Optional с найденной записью или empty если не найдено
     */
    Optional<NamingMesh> findNamingMesh(String subjectName, String className, String groupNameEducationalPlan);

    /**
     * Очищает все записи mapping между УП и МЭШ
     */
    void clearAllNamingMeshes();

    /**
     * Сохраняет список NamingMesh записей
     *
     * @param namingMeshes список записей для сохранения
     */
    void saveNamingMeshes(List<NamingMesh> namingMeshes);

    /**
     * Обновляет связи между TarifficationPerson и NamingMesh
     * Вызывает обновление связей во всех существующих записях тарификации
     */
    void updateNamingMeshRelations();

    /**
     * Проверяет существование mapping для указанных параметров
     *
     * @param subjectName название предмета
     * @param className название класса
     * @param groupNameEducationalPlan название группы по УП
     * @return true если mapping существует, false если нет
     */
    boolean existsNamingMesh(String subjectName, String className, String groupNameEducationalPlan);

    /**
     * Получает название класса по МЭШ для указанных параметров УП
     * Если mapping не найден, возвращает исходное название класса
     *
     * @param subjectName название предмета по УП
     * @param className название класса по УП
     * @param groupNameEducationalPlan название группы по УП
     * @return название класса по МЭШ или исходное название если mapping не найден
     */
    String getClassNameMesh(String subjectName, String className, String groupNameEducationalPlan);

    /**
     * Получает название группы по МЭШ для указанных параметров УП
     * Если mapping не найден, возвращает исходное название группы
     *
     * @param subjectName название предмета по УП
     * @param className название класса по УП
     * @param groupNameEducationalPlan название группы по УП
     * @return название группы по МЭШ или исходное название если mapping не найден
     */
    String getGroupNameMesh(String subjectName, String className, String groupNameEducationalPlan);

    /**
     * Получает количество записей в mapping таблице
     *
     * @return количество записей NamingMesh
     */
    long getNamingMeshCount();

    /**
     * Удаляет конкретную запись mapping по ключевым полям
     *
     * @param subjectName название предмета
     * @param className название класса
     * @param groupNameEducationalPlan название группы по УП
     * @return true если запись была удалена, false если не найдена
     */
    boolean deleteNamingMesh(String subjectName, String className, String groupNameEducationalPlan);

    /**
     * Проверяет, есть ли изменения в mapping между текущей БД и предоставленным списком
     *
     * @param newNamingMeshes новый список NamingMesh
     * @return true если есть изменения, false если данные идентичны
     */
    boolean hasChanges(List<NamingMesh> newNamingMeshes);
}