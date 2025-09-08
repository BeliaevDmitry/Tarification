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
import java.util.Map;

import static org.school.personalLoad.config.AppConfig.getOfflineFilesPath;

public class TarifficationController {

    private final DataReaderService dataReaderService;
    private final DataProcessingService dataProcessingService;
    private final ReportService reportService;
    private final DatabaseService databaseService;
    private final GroupSearchService groupSearchService;

    public TarifficationController() {
        HibernateConfig.getSessionFactory();
        this.databaseService = new DatabaseServiceImpl();
        this.dataReaderService = new DataReaderService();
        this.dataProcessingService = new DataProcessingService(databaseService);
        this.reportService = new ReportService();
        this.groupSearchService = new GroupSearchService(); // Новый сервис
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

            // 4. Сортируем историю
            List<TarifficationChanges> allHistory = databaseService.getAllHistory();
            dataProcessingService.sortHistoryByDate(allHistory);

            // 5. Поиск групп для инвалидов (новая функциональность)
            Map<String, List<String>> disabledStudentsGroups = findGroupsForDisabledStudents(inputPath);

            // 6. Создание отчета с передачей информации о группах инвалидов
            List<String> listGroup = databaseService.findAllUniqueClassAndGroupNames();
            reportService.createReport(tarifficationList, groupList, allHistory, outputPath,
                    listGroup, disabledStudentsGroups);

            System.out.println("✅ Успешно обработано: " + tarifficationList.size() + " записей");
            System.out.println("✅ Найдено изменений: " + changes.size());
            System.out.println("✅ Найдено групп для инвалидов: " + disabledStudentsGroups.size() + " студентов");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Метод для поиска групп для инвалидов
     * Использует текущий файл как онлайн-файл и указанную в конфиге офлайн-папку
     */
    private Map<String, List<String>> findGroupsForDisabledStudents(String onlineFilePath) {
        try {
            // Берем путь к офлайн папке из конфига
            String offlineFolderPath = getOfflineFilesPath();

            return groupSearchService.findGroupsForDisabledStudents(onlineFilePath, offlineFolderPath);

        } catch (Exception e) {
            System.err.println("❌ Ошибка при поиске групп для инвалидов: " + e.getMessage());
            return Map.of(); // Возвращаем пустую карту в случае ошибки
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
                } else if (sheetName.contains("контингент")) {
                    System.out.println("📊 Анализируем лист Контингент для поиска инвалидов: " + sheet.getSheetName());
                    // Этот лист будет использоваться для поиска инвалидов
                } else {
                    System.out.println("⏭️ Пропускаем лист: " + sheet.getSheetName());
                }
            }
        }
    }
}