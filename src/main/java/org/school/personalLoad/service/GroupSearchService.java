package org.school.personalLoad.service;

import org.school.personalLoad.model.GroupOrClassInfo;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс сервиса для поиска групп и сбора информации о классах
 */
public interface GroupSearchService {

    /**
     * Ищет группы для студентов-инвалидов в офлайн файлах
     *
     * @param onlineFilePath    путь к онлайн файлу с данными об инвалидах
     * @param offlineFolderPath путь к папке с офлайн файлами
     * @return карта с именами студентов и списками их групп
     * @throws Exception если произошла ошибка при обработке файлов
     */
    Map<String, List<String>> findGroupsForDisabledStudents(String onlineFilePath,
                                                            String offlineFolderPath,
                                                            String expelledFilePath) throws Exception;


    /**
     * Собирает информацию о классах, численности и преподавателях из офлайн файлов
     *
     * @param offlineFolderPath путь к папке с офлайн файлами
     * @return карта с информацией о классах
     * @throws Exception если произошла ошибка при обработке файлов
     */
    Map<String, GroupOrClassInfo> collectClassInfo(String offlineFolderPath,
                                                   String expelledFilePath) throws Exception;
}