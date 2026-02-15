package org.school.personalLoad.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.personalLoad.config.AppConfig;
import org.school.personalLoad.model.GroupOrClassInfo;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationChangesMesh;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.service.DatabaseService;
import org.school.personalLoad.service.GroupSearchService;
import org.school.personalLoad.service.NamingMeshService;
import org.school.personalLoad.service.ReportService;
import org.school.personalLoad.service.TarifficationDataReaderService;
import org.school.personalLoad.service.TarifficationNamingService;
import org.school.personalLoad.service.TarifficationProcessingService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TarifficationController {

    private final TarifficationDataReaderService tarifficationDataReaderService;
    private final TarifficationProcessingService tarifficationProcessingService;
    private final ReportService reportService;
    private final DatabaseService databaseService;
    private final GroupSearchService groupSearchService;
    private final TarifficationNamingService tarifficationNamingService;
    private final NamingMeshService namingMeshService;
    private final AppConfig appConfig;

    public void processTariffication(String inputPath, String outputPath) {
        try {
            log.info("Начинаем обработку NamingMesh из файла");
            List<TarifficationChangesMesh> namingMeshChanges = namingMeshService.processNamingMeshFile(inputPath);
            namingMeshService.sortTarifficationChangesMeshByDate(namingMeshChanges);
            log.info("Обработка NamingMesh завершена. Найдено изменений: {}", namingMeshChanges.size());

            List<TarifficationPerson> tarifficationList = new ArrayList<>();
            List<SubjectWithGroup> groupList = new ArrayList<>();
            tarifficationDataReaderService.readExcelData(inputPath, tarifficationList, groupList);

            tarifficationList = tarifficationProcessingService.addingGroup(tarifficationList, groupList);
            tarifficationProcessingService.sortByFIO(tarifficationList);
            log.info("Успешно обработано: {} записей", tarifficationList.size());

            Map<String, String[]> loadNamingMapping = tarifficationNamingService.loadNamingMapping(inputPath);
            log.info("Найдено {} записей отличия МЭШ/тарификации", loadNamingMapping.size());
            tarifficationNamingService.applyNamingMapping(tarifficationList, loadNamingMapping);

            databaseService.compareAndSave(tarifficationList);
            List<TarifficationChanges> allHistory = databaseService.getAllHistory();
            tarifficationProcessingService.sortHistoryByDate(allHistory);

            Map<String, List<String>> disabledStudentsGroups =
                    groupSearchService.findGroupsForDisabledStudents(inputPath,
                            appConfig.getOfflineFilesDirectory(), appConfig.getExpelledFilePath());
            log.info("Найдено групп для инвалидов: {} обучающихся", disabledStudentsGroups.size());

            Map<String, GroupOrClassInfo> classInfo =
                    groupSearchService.collectClassInfo(appConfig.getOfflineFilesDirectory(), appConfig.getExpelledFilePath());
            log.info("Собрана информация о {} классах", classInfo.size());

            List<String> listGroup = databaseService.findAllUniqueClassAndGroupNames();
            reportService.createReport(tarifficationList, groupList, allHistory, outputPath,
                    disabledStudentsGroups, classInfo, namingMeshChanges, listGroup);

            log.info("Отчет собран и записан в файл: {}", outputPath);

        } catch (Exception e) {
            log.error("Ошибка при обработке файла", e);
            throw new RuntimeException(e);
        }
    }
}
