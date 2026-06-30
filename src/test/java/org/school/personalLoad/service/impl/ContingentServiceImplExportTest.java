package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.school.personalLoad.model.ClassroomLeadershipEntry;
import org.school.personalLoad.model.ContingentSnapshot;
import org.school.personalLoad.repository.ClassroomLeadershipRepository;
import org.school.personalLoad.repository.ContingentSnapshotRepository;
import org.school.personalLoad.repository.ContingentStudentRepository;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.service.ClassSizeService;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContingentServiceImplExportTest {

    @Mock
    private ContingentSnapshotRepository snapshotRepository;
    @Mock
    private ContingentStudentRepository studentRepository;
    @Mock
    private ClassroomLeadershipRepository classroomLeadershipRepository;
    @Mock
    private CurriculumPlanEntryRepository curriculumPlanEntryRepository;
    @Mock
    private SchoolBuildingRepository schoolBuildingRepository;
    @Mock
    private ClassSizeService classSizeService;

    private ContingentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContingentServiceImpl(
                snapshotRepository,
                studentRepository,
                classroomLeadershipRepository,
                curriculumPlanEntryRepository,
                schoolBuildingRepository,
                classSizeService
        );
    }

    @Test
    void exportStatsCreatesBuildingAndAddressSheets() throws Exception {
        ContingentSnapshot snapshot = new ContingentSnapshot();
        snapshot.setId(42L);
        snapshot.setAcademicYear("2025/2026");
        snapshot.setSnapshotDate(LocalDate.of(2025, 9, 1));
        snapshot.setSourceFileName("contingent.xlsx");
        snapshot.setTotalStudents(5);

        ClassroomLeadershipEntry first = classEntry("СП1", "1-А", "ул. Общая, 1");
        ClassroomLeadershipEntry second = classEntry("СП2", "2-А", "ул. Общая, 1");
        ClassroomLeadershipEntry third = classEntry("СП2", "3-А", "ул. Другая, 2");

        when(snapshotRepository.findFirstByAcademicYearAndSnapshotDateOrderByImportedAtDesc("2025/2026", snapshot.getSnapshotDate()))
                .thenReturn(Optional.of(snapshot));
        when(studentRepository.findClassNamesBySnapshotId(42L)).thenReturn(List.of("1-А", "1-А", "2-А", "2-А", "2-А"));
        when(classroomLeadershipRepository.findAllByAcademicYear("2025/2026")).thenReturn(List.of(first, second, third));
        when(schoolBuildingRepository.findByCode("СП1")).thenReturn(Optional.empty());
        when(schoolBuildingRepository.findByCode("СП2")).thenReturn(Optional.empty());

        byte[] body = service.exportStats("2025/2026", snapshot.getSnapshotDate());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("По СП"));
            var addressSheet = workbook.getSheet("По адресам");
            assertNotNull(addressSheet);
            assertEquals("ул. Другая, 2", addressSheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("ул. Общая, 1", addressSheet.getRow(1).getCell(4).getStringCellValue());
            assertEquals(30D, addressSheet.getRow(5).getCell(3).getNumericCellValue(), 0.01);
            assertEquals(5D, addressSheet.getRow(5).getCell(5).getNumericCellValue(), 0.01);
        }
    }

    private ClassroomLeadershipEntry classEntry(String building, String className, String address) {
        ClassroomLeadershipEntry entry = new ClassroomLeadershipEntry();
        entry.setAcademicYear("2025/2026");
        entry.setNumberSchoolBuilding(building);
        entry.setClassName(className);
        entry.setClassDirection("общеобразовательный");
        entry.setFioTeacher("Классный руководитель");
        entry.setCampusAddress(address);
        return entry;
    }
}
