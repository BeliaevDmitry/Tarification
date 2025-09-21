package org.school.personalLoad.controller;

import org.school.personalLoad.config.HibernateConfig;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.*;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.service.impl.*;
import org.school.personalLoad.model.GroupOrClassInfo;
import org.school.personalLoad.model.TarifficationChangesMesh; // Добавляем импорт
import org.school.personalLoad.dao.TarifficationChangesMeshDAO; // Добавляем импорт



import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.school.personalLoad.config.AppConfig.getOfflineFilesPath;

public class TarifficationController {

    private final TarifficationDataReaderService tarifficationDataReaderService;
    private final TarifficationProcessingService tarifficationProcessingService;
    private final ReportService reportService;
    private final DatabaseService databaseService;
    private final GroupSearchService groupSearchService;
    private final TarifficationNamingService tarifficationNamingService;
    private final NamingMeshService namingMeshService;


    public TarifficationController() {
        HibernateConfig.getSessionFactory();
        this.databaseService = new DatabaseServiceImpl();
        this.tarifficationDataReaderService = new TarifficationDataReaderServiceImpl();
        this.tarifficationProcessingService = new TarifficationProcessingServiceImpl(databaseService);
        this.reportService = new ReportServiceImpl();
        this.groupSearchService = new GroupSearchServiceImpl(); // Новый сервис
        this.tarifficationNamingService = new TarifficationNamingServiceImpl();
        this.namingMeshService = new NamingMeshServiceImpl();
    }

    /**
     * Основной метод обработки тарификации
     */
    public void processTariffication(String inputPath, String outputPath) {
        try {
            // 1. Обработка NamingMesh из того же файла
            List<TarifficationChangesMesh> namingMeshChanges = new ArrayList<>();
            System.out.println("🔄 Начинаем обработку NamingMesh из файла...");
            namingMeshChanges = namingMeshService.processNamingMeshFile(inputPath);
            System.out.println("✅ Обработка NamingMesh завершена. Найдено изменений: " + namingMeshChanges.size());

            // 2. Чтение и обработка данных из Excel
            List<TarifficationPerson> tarifficationList = new ArrayList<>();
            List<SubjectWithGroup> groupList = new ArrayList<>();
            tarifficationDataReaderService.readExcelData(inputPath, tarifficationList, groupList);

            // 3. Обработка данных
            tarifficationList = tarifficationProcessingService.addingGroup(tarifficationList, groupList);
            tarifficationProcessingService.sortByFIO(tarifficationList);
            System.out.println("✅ Успешно обработано: " + tarifficationList.size() + " записей");

            // 4. Применение naming mapping
            Map<String, String[]> loadNamingMapping = tarifficationNamingService.loadNamingMapping(inputPath);
            System.out.println("✅ найдено " + loadNamingMapping.size() + " записей отличия от записи в МЭШ и тарификации");
            tarifficationNamingService.applyNamingMapping(tarifficationList, loadNamingMapping);
            System.out.println("✅ отличия от записи в МЭШ и тарификации добавлены");

            // 5. Сравнение с ИСТОРИЕЙ и сохранение
            databaseService.compareAndSave(tarifficationList);
            List<TarifficationChanges> allHistory = databaseService.getAllHistory();

            // 6. Сортируем историю
            tarifficationProcessingService.sortHistoryByDate(allHistory);

            // 7. Поиск групп для инвалидов
            Map<String, List<String>> disabledStudentsGroups =
                    groupSearchService.findGroupsForDisabledStudents(inputPath, getOfflineFilesPath());
            System.out.println("✅ Найдено групп для инвалидов: " + disabledStudentsGroups.size() + " обучающихся");

            // 8. Собираем информацию о классах, численности и преподавателях
            Map<String, GroupOrClassInfo> classInfo = groupSearchService.collectClassInfo(getOfflineFilesPath());
            System.out.println("✅ Собрана информация о " + classInfo.size() + " классах");

            // 9. Создание отчета с передачей информации о классах
            List<String> listGroup = databaseService.findAllUniqueClassAndGroupNames();
            reportService.createReport(tarifficationList, groupList, allHistory, outputPath,
                    disabledStudentsGroups, classInfo, namingMeshChanges);

            System.out.println("✅ отчёт собран и записан в файл ");

            } catch (Exception e) {
            System.err.println("❌ Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }
}