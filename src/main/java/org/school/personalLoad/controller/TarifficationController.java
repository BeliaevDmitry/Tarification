package org.school.personalLoad.controller;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.DataProcessingService;
import org.school.personalLoad.service.DataReaderService;
import org.school.personalLoad.service.ReportService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TarifficationController {

    private final DataProcessingService dataProcessingService;
    private final ReportService reportService;

    // Паттерн для фильтрации листов
    private static final Pattern SHEET_PATTERN = Pattern.compile(".*корп.*", Pattern.CASE_INSENSITIVE);

    public TarifficationController() {
        this.dataProcessingService = new DataProcessingService();
        this.reportService = new ReportService();
    }

    public void processTariffication(String inputPath, String outputPath) {
        processTariffication(inputPath, outputPath, SHEET_PATTERN);
    }

    public void processTariffication(String inputPath, String outputPath, Pattern sheetPattern) {
        try (Workbook workbook = WorkbookFactory.create(new File(inputPath))) {
            // Создаем FormulaEvaluator для всего workbook
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataReaderService dataReaderService = new DataReaderService(formulaEvaluator);

            List<TarifficationPerson> tariffication = new ArrayList<>();
            List<SubjectWithGroup> subjectWithGroup = new ArrayList<>();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();

                // Проверяем, нужно ли обрабатывать этот лист
                if (!shouldProcessSheet(sheetName, sheetPattern)) {
                    System.out.println("Пропускаем лист: " + sheetName);
                    continue;
                }

                System.out.println("Анализируем лист: " + sheetName);

                List<TarifficationPerson> tarifficationListCurrent = dataReaderService.analyzeSheet(sheet);
                List<SubjectWithGroup> subjectWithGroupListCurrent = dataReaderService.searchGroup(sheet);

                tarifficationListCurrent = dataProcessingService.addingGroup(
                        tarifficationListCurrent, subjectWithGroupListCurrent
                );

                tariffication.addAll(tarifficationListCurrent);
                subjectWithGroup.addAll(subjectWithGroupListCurrent);
            }

            dataProcessingService.sortByFIO(tariffication);
            reportService.createReport(tariffication, subjectWithGroup, outputPath);

            System.out.println("Отчет успешно создан: " + outputPath);
            System.out.println("Всего записей: " + tariffication.size());

        } catch (IOException | EncryptedDocumentException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean shouldProcessSheet(String sheetName, Pattern sheetPattern) {
        if (sheetPattern == null) {
            return true;
        }
        return sheetPattern.matcher(sheetName).matches();
    }
}