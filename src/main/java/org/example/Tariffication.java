package org.example;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.model.TarifficationPerson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public abstract class Tariffication {

    static {
        System.setProperty("org.apache.poi.util.POILogger", "org.apache.poi.util.NullLogger");
    }

    public static void main(String[] args) {
        String inputPath = "C:\\Users\\dimah\\\\Desktop\\1.xlsx";
        String outputPath = "C:\\Users\\dimah\\\\Desktop\\report.xlsx";

        try (Workbook workbook = WorkbookFactory.create(new File(inputPath))) {
            List<TarifficationPerson> Tariffication = new ArrayList<>();

            // Перебираем все листы в книге
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                System.out.println("Анализируем лист: " + sheet.getSheetName());

                List<TarifficationPerson> TarifficationListCurrent = analyzeSheet(sheet);
                Tariffication.addAll(TarifficationListCurrent);
            }

            // Сортируем итоговый список по ФИО
            sortByFIO(Tariffication);
            createReport(Tariffication, outputPath);
            System.out.println("Отчет успешно создан: " + outputPath);
            System.out.println("Всего записей: " + Tariffication.size());

        } catch (IOException | EncryptedDocumentException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static List<TarifficationPerson> analyzeSheet(Sheet sheet) throws IOException {
        List<TarifficationPerson> TarifficationList = new ArrayList<>();

        for (int currentRow = 0; currentRow <= sheet.getLastRowNum(); currentRow++) {
            Row teacherRow = sheet.getRow(currentRow);

            int loadColumn = 12;
            int loadClass = 1;

            // ПРАВИЛЬНОЕ получение значений из ячеек
            String subject = getCellValueAsString(teacherRow.getCell(0)).trim();
            String fioTeacher = getCellValueAsString(teacherRow.getCell(1)).trim();
            int teacherLoadInRow = getCellValueAsInt(teacherRow.getCell(2));

            if (fioTeacher.equals("по УП") ||
                    fioTeacher.equals("выставлено выше") ||
                    fioTeacher.equals("выставлено") ||
                    fioTeacher.equals("количество групп")) {
                continue;
            }

            if (!subject.isEmpty() || !fioTeacher.isEmpty() || teacherLoadInRow > 0) {
                for (int currentColumn = loadColumn; currentColumn <= teacherRow.getLastCellNum(); currentColumn++) {
                    Cell currentCell = teacherRow.getCell(currentColumn);
                    if (currentCell == null) continue;

                    int currentLoad = getCellValueAsInt(currentCell);
                    String numberSchoolBuilding = sheet.getSheetName();

                    if (currentLoad > 0) {
                        Row classRow = sheet.getRow(loadClass);
                        if (classRow == null) continue;

                        Cell classCell = classRow.getCell(currentColumn);
                        String classLoad = getCellValueAsString(classCell);
                        if (classLoad.equals("1-4кл") ||
                                classLoad.equals("5-9кл") ||
                                classLoad.equals("сш")) {
                            continue;
                        }

                        TarifficationList.add(new TarifficationPerson(
                                fioTeacher,
                                numberSchoolBuilding,
                                subject,
                                classLoad,
                                currentLoad
                        ));
                    }
                }
            }
        }
        return TarifficationList;
    }

    static void createReport(List<TarifficationPerson> Tariffication, String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Тарификация");

            // Заголовки
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ФИО педагога", "Корпус", "Предмет", "Класс", "группа", "Количество часов"};
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Данные
            int rowNum = 1;
            for (TarifficationPerson record : Tariffication) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.getFioTeacher());
                row.createCell(1).setCellValue(record.getNumberSchoolBuilding());
                row.createCell(2).setCellValue(record.getSubject());
                row.createCell(3).setCellValue(record.getClassLoad());
                row.createCell(4).setCellValue(record.getGroupLoad());
                row.createCell(5).setCellValue(record.getLoad());
            }

            // Авторазмер колонок
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
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

    private static void addingsGroup(List<TarifficationPerson> list) {
        list.sort(Comparator.comparing(TarifficationPerson::getSubject));
        list.sort(Comparator.comparing(TarifficationPerson::getClassLoad));
    }


}


