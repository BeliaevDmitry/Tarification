package org.school.personalLoad.service;

import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.List;

/**
 * Интерфейс сервиса для обработки данных тарификации
 */
public interface TarifficationProcessingService {

    /**
     * Добавляет информацию о группах к данным тарификации
     *
     * @param list список персоналий тарификации
     * @param groupList список предметов с группами
     * @return обработанный список с информацией о группах
     */
    List<TarifficationPerson> addingGroup(List<TarifficationPerson> list,
                                          List<SubjectWithGroup> groupList);

    /**
     * Сортирует список персоналий тарификации по ФИО преподавателя
     *
     * @param list список для сортировки
     */
    void sortByFIO(List<TarifficationPerson> list);

    /**
     * Сортирует историю изменений тарификации по дате
     *
     * @param historyList список изменений для сортировки
     */
    void sortHistoryByDate(List<TarifficationChanges> historyList);
}