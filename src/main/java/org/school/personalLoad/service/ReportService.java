package org.school.personalLoad.service;

import org.school.personalLoad.model.GroupOrClassInfo;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс сервиса для создания отчетов в Excel
 */
public interface ReportService {

    /**
     * Создает комплексный отчет в Excel файле
     *
     * @param tarifficationList список персоналий тарификации
     * @param subjectWithGroupList список предметов с группами
     * @param changes история изменений тарификации
     * @param outputPath путь для сохранения файла отчета
     * @param listGroup список уникальных названий групп/классов
     * @param disabledStudentsGroups карта групп для студентов-инвалидов
     * @param classInfo информация о классах из журналов
     * @throws IOException если произошла ошибка при создании файла
     */
    void createReport(List<TarifficationPerson> tarifficationList,
                      List<SubjectWithGroup> subjectWithGroupList,
                      List<TarifficationChanges> changes,
                      String outputPath,
                      List<String> listGroup,
                      Map<String, List<String>> disabledStudentsGroups,
                      Map<String, GroupOrClassInfo> classInfo,
                      List<TarifficationChanges> namingMeshChanges) throws IOException;
}