package org.school.ordergen.service;

import org.school.ordergen.config.AppConfig;
import org.school.ordergen.model.OrderData;
import org.school.ordergen.model.StudentInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

@Slf4j
@Service
public class TemplateFillerService {

    public void fillTemplate(OrderData data, String fileName) throws Exception {
        String templatePath = AppConfig.TEMPLATE_PATH;
        String outputPath = AppConfig.OUTPUT_PATH;

        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(templatePath))) {
            // Замена в параграфах
            for (XWPFParagraph p : doc.getParagraphs()) {
                replaceInParagraph(p, data);
            }

            // Замена в таблицах
            for (XWPFTable tbl : doc.getTables()) {
                for (XWPFTableRow row : tbl.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            replaceInParagraph(p, data);
                        }
                    }
                }
            }

            // Вставка таблицы учеников
            insertStudentsTable(doc, data.getStudents());

            // Вставка строки сопровождающих
            replacePlaceholder(doc, "{accompanying}", data.getAccompanying());

            // Сохранение документа
            String outputFile = outputPath + "/" + fileName;
            File outputDir = new File(outputPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                doc.write(out);
            }
            log.info("Сгенерирован приказ: {}", outputFile);
        }
    }

    private void replaceInParagraph(XWPFParagraph p, OrderData data) {
        String text = p.getText();
        String newText = text;
        newText = newText.replace("{eventDate}", data.getEventDate());
        newText = newText.replace("{className}", data.getClassName());
        newText = newText.replace("{classWord}", data.getClassWord());
        newText = newText.replace("{number}", data.getNumber());
        newText = newText.replace("{venue}", data.getVenue());
        newText = newText.replace("{address}", data.getAddress());
        newText = newText.replace("{eventTime}", data.getEventTime());
        newText = newText.replace("{leader}", data.getLeader());
        newText = newText.replace("{deputy}", data.getDeputy());
        newText = newText.replace("{leaderName}", data.getLeaderName());
        newText = newText.replace("{gatheringTime}", data.getGatheringTime());
        newText = newText.replace("{gatheringPlace}", data.getGatheringPlace());
        newText = newText.replace("{returnTime}", data.getReturnTime());
        newText = newText.replace("{curator}", data.getCurator());
        newText = newText.replace("{leaderDative}", data.getLeaderDative());
        newText = newText.replace("{accompanyingTitle}", data.getAccompanyingTitle());

        if (!text.equals(newText)) {
            // Очищаем параграф от старых run'ов
            while (p.getRuns().size() > 0) {
                p.removeRun(0);
            }

            // Обработка маркеров разрыва [br]
            if (newText.contains("[br]")) {
                String[] parts = newText.split("\\[br\\]");
                for (int i = 0; i < parts.length; i++) {
                    // Создаём run с текущей частью (даже если пустая)
                    XWPFRun run = p.createRun();
                    run.setFontFamily("Times New Roman");
                    run.setFontSize(14);
                    run.setText(parts[i]);
                    // Добавляем разрыв после каждой части, кроме последней
                    if (i < parts.length - 1) {
                        run.addBreak();
                    }
                }
            } else {
                // Если маркеров нет, создаём один run
                XWPFRun run = p.createRun();
                run.setFontFamily("Times New Roman");
                run.setFontSize(14);
                run.setText(newText);
            }

            // Устанавливаем выравнивание по ширине и одинарный интервал
            p.setSpacingBetween(1.0);
            p.setAlignment(ParagraphAlignment.BOTH);
        }
    }

    private void replacePlaceholder(XWPFDocument doc, String placeholder, String value) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            String text = p.getText();
            if (text.contains(placeholder)) {
                // Заменяем плейсхолдер на value (которое может содержать \n)
                String newText = text.replace(placeholder, value);

                // Очищаем старые run'ы
                while (p.getRuns().size() > 0) {
                    p.removeRun(0);
                }

                // Разбиваем текст по переводам строк и вставляем с разрывами
                String[] lines = newText.split("\n", -1); // -1 чтобы сохранить пустые строки
                for (int i = 0; i < lines.length; i++) {
                    XWPFRun run = p.createRun();
                    run.setFontFamily("Times New Roman");
                    run.setFontSize(14);
                    run.setText(lines[i]);

                    // После каждой строки, кроме последней, добавляем разрыв
                    if (i < lines.length - 1) {
                        run.addBreak();
                    }
                }

                // Устанавливаем форматирование параграфа (если нужно)
                p.setSpacingBetween(1.0);
                //p.setAlignment(ParagraphAlignment.BOTH);

                break; // Нашли нужный параграф – выходим
            }
        }
    }

    private void insertStudentsTable(XWPFDocument doc, List<StudentInfo> students) {
        // Находим параграф с маркером {studentsTable}
        int targetPos = -1;
        for (int i = 0; i < doc.getBodyElements().size(); i++) {
            var elem = doc.getBodyElements().get(i);
            if (elem instanceof XWPFParagraph) {
                XWPFParagraph p = (XWPFParagraph) elem;
                if (p.getText().contains("{studentsTable}")) {
                    targetPos = i;
                    break;
                }
            }
        }
        if (targetPos == -1) {
            log.warn("Маркер {studentsTable} не найден");
            return;
        }

        // Удаляем этот параграф
        doc.removeBodyElement(targetPos);

        // Создаем таблицу
        XWPFTable table = doc.createTable(students.size() + 1, 4);
        table.setWidth("100%");

        // Заголовок
        XWPFTableRow header = table.getRow(0);
        setCellText(header.getCell(0), "№");
        setCellText(header.getCell(1), "ФИО");
        setCellText(header.getCell(2), "ФИО представителя");
        setCellText(header.getCell(3), "Телефон представителя");

        // Данные
        int rowNum = 1;
        for (StudentInfo s : students) {
            XWPFTableRow row = table.getRow(rowNum);
            setCellText(row.getCell(0), String.valueOf(rowNum));
            setCellText(row.getCell(1), s.getFullName() != null ? s.getFullName() : "");
            setCellText(row.getCell(2), s.getParentName() != null ? s.getParentName() : "");
            setCellText(row.getCell(3), s.getParentPhone() != null ? s.getParentPhone() : "");
            rowNum++;
        }

        // Применяем шрифт ко всем ячейкам (можно и не вызывать, т.к. setCellText уже задаёт шрифт)
        // Но для надёжности пройдём по всем ячейкам
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    for (XWPFRun run : p.getRuns()) {
                        run.setFontFamily("Times New Roman");
                        run.setFontSize(14);
                    }
                }
            }
        }

        log.info("Таблица учеников вставлена. Проверьте расположение в документе.");
    }

    private void setCellText(XWPFTableCell cell, String text) {
        cell.setText(text);
        XWPFParagraph p = cell.getParagraphs().get(0);
        if (p.getRuns().isEmpty()) {
            p.createRun().setText(text);
        }
        for (XWPFRun run : p.getRuns()) {
            run.setFontFamily("Times New Roman");
            run.setFontSize(14);
        }
    }
}