package org.school.personalLoad.service;

import org.school.personalLoad.model.TarifficationPerson;
import java.util.List;
import java.util.Map;

public interface TarifficationNamingService {

    /**
     * Читает файл и возвращает маппинг для переименования
     * Ключ: "корпус|предмет|класс|группа"
     * Значение: список [classNameMesh, groupNameMesh, флаг_применения]
     */
    Map<String, String[]> loadNamingMapping(String excelFilePath);

    /**
     * Применяет маппинг названий к списку тарификации
     * @param list - список тарификации для обработки
     * @param namingMapping - маппинг из loadNamingMapping()
     */
    void applyNamingMapping(List<TarifficationPerson> list,
                            Map<String, String[]> namingMapping);
}