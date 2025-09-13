package org.school.personalLoad.controller;

import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.*;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.service.impl.*;
import org.school.personalLoad.model.GroupOrClassInfo;

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
        this.dataReaderService = new DataReaderServiceImpl();
        this.dataProcessingService = new DataProcessingServiceImpl(databaseService);
        this.reportService = new ReportServiceImpl();
        this.groupSearchService = new GroupSearchServiceImpl(); // Новый сервис
    }

    public void processTariffication(String inputPath, String outputPath) {
        try {
            // 1. Чтение и обработка данных из Excel
            List<TarifficationPerson> tarifficationList = new ArrayList<>();
            List<SubjectWithGroup> groupList = new ArrayList<>();
            dataReaderService.readExcelData(inputPath, tarifficationList, groupList);

            // 2. Обработка данных
            dataProcessingService.addingGroup(tarifficationList, groupList);
            dataProcessingService.sortByFIO(tarifficationList);

            System.out.println("✅ Успешно обработано: " + tarifficationList.size() + " записей");

            // 3. Сравнение с ИСТОРИЕЙ и сохранение
            List<TarifficationChanges> changes = databaseService.compareAndSave(tarifficationList);
            List<TarifficationChanges> allHistory = databaseService.getAllHistory();

            // 4. Сортируем историю
            dataProcessingService.sortHistoryByDate(allHistory);

            // 5. Поиск групп для инвалидов
            Map<String, List<String>> disabledStudentsGroups =
                    groupSearchService.findGroupsForDisabledStudents(inputPath, getOfflineFilesPath());

            System.out.println("✅ Найдено групп для инвалидов: " + disabledStudentsGroups.size() + " обучающихся");

            // 7. Собираем информацию о классах, численности и преподавателях
            Map<String, GroupOrClassInfo> classInfo = groupSearchService.collectClassInfo(getOfflineFilesPath());

            System.out.println("✅ Собрана информация о " + classInfo.size() + " классах");

            // 8. Создание отчета с передачей информации о классах
            List<String> listGroup = databaseService.findAllUniqueClassAndGroupNames();
            reportService.createReport(tarifficationList, groupList, allHistory, outputPath,
                    listGroup, disabledStudentsGroups, classInfo);

            System.out.println("✅ отчёт собран и записан в файл ");




        } catch (Exception e) {
            System.err.println("❌ Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }
}