package org.school.personalLoad.service;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

import java.io.IOException;
import java.util.List;

/**
 * Интерфейс сервиса для чтения данных из Excel файлов тарификации
 */
public interface DataReaderService {

    /**
     * Устанавливает FormulaEvaluator для вычисления формул в Excel
     *
     * @param formulaEvaluator оценщик формул Apache POI
     */
    void setFormulaEvaluator(FormulaEvaluator formulaEvaluator);

    /**
     * Ищет информацию о группах (предметах с делением на группы) в листе Excel
     *
     * @param sheet лист Excel для поиска
     * @return список предметов с информацией о группах
     */
    List<SubjectWithGroup> searchGroup(Sheet sheet);

    void readExcelData(String inputPath,
                       List<TarifficationPerson> tarifficationList,
                       List<SubjectWithGroup> groupList) throws Exception;
}