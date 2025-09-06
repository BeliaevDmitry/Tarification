package org.school.personalLoad.controller;

import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.*;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.service.impl.DatabaseServiceImpl;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class TarifficationController {

    private final DataReaderService dataReaderService;
    private final DataProcessingService dataProcessingService;
    private final ReportService reportService;
    private final DatabaseService databaseService;

    public TarifficationController() {
        HibernateConfig.getSessionFactory();
        this.dataReaderService = new DataReaderService();
        this.dataProcessingService = new DataProcessingService();
        this.reportService = new ReportService();
        this.databaseService = new DatabaseServiceImpl();
    }

    public void processTariffication(String inputPath, String outputPath) {
        try {
            // 1. Чтение и обработка данных из Excel
            List<TarifficationPerson> tarifficationList = new ArrayList<>();
            List<SubjectWithGroup> groupList = new ArrayList<>();
            readExcelData(inputPath, tarifficationList, groupList);

            // 2. Обработка данных
            dataProcessingService.addingGroup(tarifficationList, groupList);
            dataProcessingService.sortByFIO(tarifficationList);

            // 3. Сравнение с ИСТОРИЕЙ и сохранение
            List<TarifficationChanges> changes = databaseService.compareAndSave(tarifficationList);

            // 4. Сортируем
            List<TarifficationChanges> allHistory = databaseService.getAllHistory();
            dataProcessingService.sortHistoryByDate(allHistory);

            // 5. Создание отчета
            reportService.createReport(tarifficationList, groupList, allHistory, outputPath);

            System.out.println("✅ Успешно обработано: " + tarifficationList.size() + " записей");
            System.out.println("✅ Найдено изменений: " + changes.size());

        } catch (Exception e) {
            System.err.println("❌ Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void readExcelData(String inputPath,
                               List<TarifficationPerson> tarifficationList,
                               List<SubjectWithGroup> groupList) throws Exception {
        try (FileInputStream fis = new FileInputStream(new File(inputPath));
             Workbook workbook = WorkbookFactory.create(fis)) {

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            dataReaderService.setFormulaEvaluator(evaluator);

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName().toLowerCase();

                if (sheetName.contains("корп")) {
                    System.out.println("📊 Анализируем лист: " + sheet.getSheetName());
                    tarifficationList.addAll(dataReaderService.analyzeSheet(sheet));
                    groupList.addAll(dataReaderService.searchGroup(sheet));
                } else {
                    System.out.println("⏭️ Пропускаем лист: " + sheet.getSheetName());
                }
            }
        }
    }
}