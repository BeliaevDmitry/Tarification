package org.school.personalLoad.service;

import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;

import java.util.List;

/**
 * Интерфейс для работы с данными тарификации
 */
public interface DatabaseService {

    /**
     * Сравнивает новую тарификацию с предыдущей и сохраняет изменения
     * @param newTariffication список новых данных тарификации
     * @return список обнаруженных изменений
     */
    List<TarifficationChanges> compareAndSave(List<TarifficationPerson> newTariffication);

    /**
     * Сравнивает новую тарификацию с историей изменений
     * @param newTariffication список новых данных тарификации
     * @return список обнаруженных изменений
     */
    List<TarifficationChanges> compareWithHistory(List<TarifficationPerson> newTariffication);

    /**
     * Сохраняет текущую версию тарификации в базу данных
     * @param tarifficationList список данных тарификации для сохранения
     */
    void saveCurrentTariffication(List<TarifficationPerson> tarifficationList);

    /**
     * Полностью очищает историю и текущую тарификацию
     */
    void fullReset();

    /**
     * Возвращает всю историю изменений
     * @return список всех записей истории
     */
    List<TarifficationChanges> getAllHistory();
}