package org.school;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.personalLoad.model.SubjectWithGroup;
import org.school.personalLoad.model.TarifficationPerson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Tariffication {

    static {
        System.setProperty("org.apache.poi.util.POILogger", "org.apache.poi.util.NullLogger");
    }

    public static void main(String[] args) {
        String inputPath = "C:\\Users\\dimah\\\\Desktop\\1.xlsx";
        String outputPath = "C:\\Users\\dimah\\\\Desktop\\report.xlsx";

        try (Workbook workbook = WorkbookFactory.create(new File(inputPath))) {
            List<TarifficationPerson> Tariffication = new ArrayList<>();
            List<SubjectWithGroup> SubjectWithGroup = new ArrayList<>();

            // Перебираем все листы в книге
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                System.out.println("Анализируем лист: " + sheet.getSheetName());

                List<TarifficationPerson> TarifficationListCurrent = analyzeSheet(sheet);
                List<SubjectWithGroup> SubjectWithGroupListCurrent = searchGroup(sheet);
                TarifficationListCurrent = addingGroup(TarifficationListCurrent, SubjectWithGroupListCurrent);

                Tariffication.addAll(TarifficationListCurrent);
                SubjectWithGroup.addAll(SubjectWithGroupListCurrent);
            }

            // Сортируем итоговый список по ФИО
            sortByFIO(Tariffication);
            createReport(Tariffication, SubjectWithGroup, outputPath);
            System.out.println("Отчет успешно создан: " + outputPath);
            System.out.println("Всего записей: " + Tariffication.size());

        } catch (IOException | EncryptedDocumentException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static List<TarifficationPerson> analyzeSheet(Sheet sheet) throws IOException {
        List<TarifficationPerson> TarifficationList = new ArrayList<>();

        for (int numberCurrentRow = 0; numberCurrentRow <= sheet.getLastRowNum(); numberCurrentRow++) {
            Row teacherRow = sheet.getRow(numberCurrentRow);

            int numberColumnLoad = 12;
            int numberRowLoadClass = 1;

            // ПРАВИЛЬНОЕ получение значений из ячеек
            String subjectName = getCellValueAsString(teacherRow.getCell(0)).trim();
            String fioTeacher = getCellValueAsString(teacherRow.getCell(1)).trim();
            int teacherLoadInRow = getCellValueAsInt(teacherRow.getCell(2));

            if (fioTeacher.equals("по УП") ||
                    fioTeacher.equals("выставлено выше") ||
                    fioTeacher.equals("выставлено") ||
                    fioTeacher.equals("количество групп")) {
                continue;
            }

            if (!subjectName.isEmpty() || !fioTeacher.isEmpty() || teacherLoadInRow > 0) {
                for (int currentColumn = numberColumnLoad; currentColumn <= teacherRow.getLastCellNum(); currentColumn++) {
                    Cell currentCell = teacherRow.getCell(currentColumn);
                    if (currentCell == null) continue;

                    int currentLoad = getCellValueAsInt(currentCell);
                    String numberSchoolBuilding = sheet.getSheetName();

                    if (currentLoad > 0) {
                        Row classRow = sheet.getRow(numberRowLoadClass);
                        if (classRow == null) continue;

                        Cell classCell = classRow.getCell(currentColumn);
                        String className = getCellValueAsString(classCell);
                        if (className.equals("1-4кл") ||
                                className.equals("5-9кл") ||
                                className.equals("сш")) {
                            continue;
                        }

                        TarifficationList.add(new TarifficationPerson(
                                fioTeacher,
                                numberSchoolBuilding,
                                subjectName,
                                className,
                                currentLoad
                        ));
                    }
                }
            }
        }
        return TarifficationList;
    }

    static void createReport(List<TarifficationPerson> tarifficationList,
                             List<SubjectWithGroup> subjectWithGroupList,
                             String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            // Первый лист - Тарификация
            Sheet tarifficationSheet = workbook.createSheet("Тарификация");
            createTarifficationSheet(tarifficationSheet, tarifficationList);

            // Второй лист - Группы
            Sheet groupsSheet = workbook.createSheet("Группы");
            createGroupsSheet(groupsSheet, subjectWithGroupList);

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
    }

    private static void createTarifficationSheet(Sheet sheet, List<TarifficationPerson> tarifficationList) {
        // Заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс", "группа", "Количество часов", "Количество часов в группе"};
        sheet.createFreezePane(0, 1);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);

            // Стиль для заголовков
            CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cell.setCellStyle(headerStyle);
        }

        // Данные
        int rowNum = 1;
        for (TarifficationPerson record : tarifficationList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(record.getFioTeacher());
            row.createCell(1).setCellValue(record.getNumberSchoolBuilding());
            row.createCell(2).setCellValue(record.getSubjectName());
            row.createCell(3).setCellValue(record.getClassName());
            row.createCell(4).setCellValue(record.getGroupName());
            row.createCell(5).setCellValue(record.getLoad());
            row.createCell(6).setCellValue(record.getGroupLoad());

        }

        // Авторазмер колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void createGroupsSheet(Sheet sheet, List<SubjectWithGroup> subjectWithGroupList) {
        // Заголовки для листа групп
        Row headerRow = sheet.createRow(0);
        String[] headers = {"корпус", "Предмет", "Класс", "Количество групп"};
        sheet.createFreezePane(0, 1);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);

            // Стиль для заголовков
            CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cell.setCellStyle(headerStyle);
        }

        // Данные о группах
        int rowNum = 1;
        for (SubjectWithGroup group : subjectWithGroupList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(group.getNumberSchoolBuilding());
            row.createCell(1).setCellValue(group.getSubjectName());
            row.createCell(2).setCellValue(group.getClassName());
            row.createCell(3).setCellValue("Деление на группы");
        }

        // Авторазмер колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static int getCellValueAsInt(Cell cell) {
        if (cell == null) return 0;

        switch (cell.getCellType()) {
            case NUMERIC:
                return (int) cell.getNumericCellValue();
            case STRING:
                try {
                    return Integer.parseInt(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            default:
                return 0;
        }
    }

    // Метод для сортировки списка по ФИО педагога
    private static void sortByFIO(List<TarifficationPerson> list) {
        list.sort(Comparator.comparing(person -> person.getFioTeacher()));
    }

    public static List<TarifficationPerson> addingGroup(List<TarifficationPerson> list, List<SubjectWithGroup> groupList) {
        // Создаем Set для быстрого поиска совпадений по subjectName + className
        Set<String> groupKeys = new HashSet<>();

        for (SubjectWithGroup group : groupList) {

            List<TarifficationPerson> listMatches = findAllByFields(list, group.getSubjectName(), group.getClassName());
            if (listMatches.size() == 1) {
                String nameSubject = listMatches.get(0).getSubjectName();
                String className = listMatches.get(0).getClassName();
                Integer groupLoad = listMatches.get(0).getLoad() / 2;

                // Создаем копию для второй группы
                TarifficationPerson secondGroup = new TarifficationPerson(listMatches.get(0));

                listMatches.get(0).setGroupName(nameSubject + " " + className + " ГР-1");
                listMatches.get(0).setGroupLoad(groupLoad);

                secondGroup.setGroupName(nameSubject + " " + className + " ГР-2");
                secondGroup.setGroupLoad(groupLoad);

                removeByFields(list, nameSubject, className);
                list.add(listMatches.get(0));
                list.add(secondGroup);
            } else if (listMatches.size() == 2) {
                if ((listMatches.get(0).getGroupName() == null || listMatches.get(0).getGroupName().isEmpty()) &&
                        (listMatches.get(1).getGroupName() == null || listMatches.get(1).getGroupName().isEmpty())) {
                    String nameSubject = listMatches.get(0).getSubjectName();
                    String className = listMatches.get(0).getClassName();

                    listMatches.get(0).setGroupName(nameSubject + " " + className + " ГР-1");

                    listMatches.get(1).setGroupName(nameSubject + " " + className + " ГР-2");

                    removeByFields(list, nameSubject, className);
                    list.add(listMatches.get(0));
                    list.add(listMatches.get(1));
                }

            }

        }
        return list;
    }

    private static void removeByFields(List<TarifficationPerson> list,
                                       String targetSubject, String targetClass) {
        Iterator<TarifficationPerson> iterator = list.iterator();
        while (iterator.hasNext()) {
            TarifficationPerson person = iterator.next();
            if (person.getSubjectName().equals(targetSubject) &&
                    person.getClassName().equals(targetClass)) {
                iterator.remove();
            }
        }
    }

    private static List<TarifficationPerson> findAllByFields(List<TarifficationPerson> list,
                                                            String subject, String className) {
        return list.stream()
                .filter(person -> person.getSubjectName().equals(subject) &&
                        person.getClassName().equals(className))
                .collect(Collectors.toList());
    }


    public static List<SubjectWithGroup> searchGroup
            (Sheet sheet) {
        List<SubjectWithGroup> SubjectWithGroupList = new ArrayList<>();
        String numberSchoolBuilding = sheet.getSheetName();

        for (int numberCurrentRow = 0; numberCurrentRow <= sheet.getLastRowNum(); numberCurrentRow++) {
            Row CurrentRow = sheet.getRow(numberCurrentRow);
            int numberColumnLoad = 12;
            int numberRowLoadClass = 1;

            String division = getCellValueAsString(CurrentRow.getCell(1)).trim();

            if (division.equals("количество групп")) {

                for (int currentColumn = numberColumnLoad; currentColumn <= CurrentRow.getLastCellNum(); currentColumn++) {

                    Cell currentCell = CurrentRow.getCell(currentColumn);

                    if (currentCell == null) continue;
                    int numberOfGroups = getCellValueAsInt(currentCell);
                    int hoursSet = getCellValueAsInt(sheet.getRow(numberCurrentRow + 1).getCell(currentColumn));
                    if (numberOfGroups == 2 && hoursSet > 0) {
                        String subjectName = getCellValueAsString(sheet.getRow(numberCurrentRow - 1).getCell(0)).trim();
                        Row classRow = sheet.getRow(numberRowLoadClass);
                        if (classRow == null) continue;
                        String className = getCellValueAsString(classRow.getCell(currentColumn)).trim();
                        SubjectWithGroupList.add(new SubjectWithGroup(
                                subjectName,
                                className,
                                numberSchoolBuilding
                        ));
                    }
                }
            } else {
                continue;
            }
        }
        return SubjectWithGroupList;
    }
}


