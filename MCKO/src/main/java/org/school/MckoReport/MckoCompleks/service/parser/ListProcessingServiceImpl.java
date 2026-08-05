package org.school.MckoReport.MckoCompleks.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.service.parser.ListProcessingService;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.school.MckoReport.MckoCompleks.util.SubjectNormalizerUtil;
import org.springframework.stereotype.Service;


import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ListProcessingServiceImpl implements ListProcessingService {

    /**
     * Метод принимает адреса файлов, обрабатывает только "Списки участников"
     * возвращает списки студентов и их коды для сопоставления
     *
     * @throws ProcessingException если файл не может быть обработан
     */
    @Override
    public List<ListStudentData> extractStudentsCodFromArchive(ArchiveEntry archiveEntry) {
        log.info("Начало обработки списка участников: {}", archiveEntry.getEntryPath());

        try {
            log.debug("Обработка файла: {}", archiveEntry.getEntryPath());

            // Получаем данные из файла в архиве
            byte[] fileContent = extractFileFromArchive(archiveEntry);
            if (fileContent == null || fileContent.length == 0) {
                throw new ProcessingException("Файл пуст или не найден: " + archiveEntry.getEntryPath());
            }

            // Обрабатываем PDF файл
            List<ListStudentData> students = processPdfFile(
                    fileContent,
                    archiveEntry.getEntryPath()
            );

            log.info("Обработка завершена. Найдено студентов: {}", students.size());
            return students;

        } catch (ProcessingException e) {
            log.error("Ошибка обработки файла {}: {}", archiveEntry.getEntryPath(), e.getMessage());
            throw e; // Пробрасываем дальше для @Transactional отката
        } catch (Exception e) {
            log.error("Неожиданная ошибка при обработке файла {}: {}",
                    archiveEntry.getEntryPath(), e.getMessage(), e);
            throw new ProcessingException("Ошибка обработки файла: " + archiveEntry.getEntryPath(), e);
        }
    }

    /**
     * Извлекает содержимое файла из архива
     */
    private byte[] extractFileFromArchive(ArchiveEntry archiveEntry) throws IOException {
        Path archivePath = archiveEntry.getArchivePath();
        String entryPath = archiveEntry.getEntryPath();

        log.debug("Извлечение файла из архива: {} -> {}",
                archivePath.getFileName(), entryPath);

        // Используем кодировку IBM866 (CP866), которая сработала при чтении списка файлов
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             ZipInputStream zis = new ZipInputStream(fis, Charset.forName("IBM866"))) {

            ZipEntry zipEntry;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            while ((zipEntry = zis.getNextEntry()) != null) {
                String currentEntryName = zipEntry.getName();

                // Сравниваем имена файлов (ищем нужный файл)
                if (entryPath.equals(currentEntryName)) {
                    // Нашли нужный файл
                    int length;
                    while ((length = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, length);
                    }

                    byte[] content = baos.toByteArray();
                    zis.closeEntry();
                    baos.close();

                    if (content.length == 0) {
                        log.warn("Файл {} извлечен, но пустой", entryPath);
                    }

                    log.debug("Успешно извлечено: {} байт", content.length);
                    return content;
                }
                zis.closeEntry();
            }

            // Если не нашли по точному совпадению, ищем с учетом возможных различий
            return searchFileInArchive(archivePath, entryPath);

        } catch (Exception e) {
            log.error("Ошибка извлечения файла: {}", e.getMessage());
            throw new IOException("Не удалось извлечь файл " + entryPath +
                    " из архива: " + e.getMessage(), e);
        }
    }

    /**
     * Поиск файла в архиве с разными вариантами сравнения
     */
    private byte[] searchFileInArchive(Path archivePath, String targetEntryPath) throws IOException {
        // Пробуем разные кодировки
        Charset[] charsets = {
                Charset.forName("IBM866"),      // CP866 - DOS Russian
                Charset.forName("Windows-1251"), // Windows Cyrillic
                StandardCharsets.UTF_8,
                Charset.defaultCharset()
        };

        for (Charset charset : charsets) {
            try (FileInputStream fis = new FileInputStream(archivePath.toFile());
                 ZipInputStream zis = new ZipInputStream(fis, charset)) {

                ZipEntry zipEntry;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];

                while ((zipEntry = zis.getNextEntry()) != null) {
                    String currentEntryName = zipEntry.getName();

                    // Разные способы сравнения
                    if (isMatchingEntry(targetEntryPath, currentEntryName)) {
                        // Извлекаем файл
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, length);
                        }

                        byte[] content = baos.toByteArray();
                        zis.closeEntry();
                        baos.close();

                        log.info("Найден файл с кодировкой {}: {} -> {}",
                                charset.name(), currentEntryName, targetEntryPath);

                        return content;
                    }
                    zis.closeEntry();
                }

            } catch (Exception e) {
                log.debug("Поиск с кодировкой {} не удался: {}", charset.name(), e.getMessage());
            }
        }

        throw new IOException("Файл " + targetEntryPath + " не найден в архиве");
    }

    /**
     * Сравнивает имена файлов с учетом возможных различий
     */
    private boolean isMatchingEntry(String targetPath, String currentPath) {
        // 1. Точное совпадение
        if (targetPath.equals(currentPath)) {
            return true;
        }

        // 2. Без учета регистра
        if (targetPath.equalsIgnoreCase(currentPath)) {
            return true;
        }

        // 3. Сравниваем только имена файлов (без пути)
        String targetFileName = getFileName(targetPath);
        String currentFileName = getFileName(currentPath);

        if (targetFileName.equalsIgnoreCase(currentFileName)) {
            return true;
        }

        // 4. Сравниваем нормализованные пути
        String normalizedTarget = normalizePath(targetPath);
        String normalizedCurrent = normalizePath(currentPath);

        return normalizedTarget.equals(normalizedCurrent);
    }

    /**
     * Извлекает имя файла из пути
     */
    private String getFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Нормализует путь (убирает лишние разделители, приводит к единому формату)
     */
    private String normalizePath(String path) {
        return path.replace('\\', '/')
                .replaceAll("/+", "/")
                .toLowerCase();
    }

    /**
     * Обрабатывает PDF файл и извлекает данные студентов
     */
    private List<ListStudentData> processPdfFile(byte[] pdfContent, String fileName) throws IOException {
        List<ListStudentData> students = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfContent)) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.debug("PDF TEXT [{}]:\n{}", fileName, text);

            // Извлекаем метаданные из имени файла
            String date = extractDateFromFileName(fileName);
            log.debug("Извлекаем метаданные из имени файла String date {}" +
                    "fileName {}", date, fileName);
            date =DateNormalizerUtil.normalizeDateWithFileFallback(date,fileName);
            log.debug("Извлекаем метаданные из имени файла String date " +
                    "после DateNormalizerUtil {}", date);


            // Извлекаем все метаданные
            Map<String, String> metadata = extractMetadataFromText(text);
            String school = metadata.getOrDefault("school", "");
            String className = metadata.getOrDefault("className", "");
            String subject = metadata.getOrDefault("subject", "");

            validateMetadata(fileName, date, className, subject);


            List<ListStudentData> rawStudents = extractStudentsFromText(text);

            for (ListStudentData rawStudent : rawStudents) {
                ListStudentData student = ListStudentData.builder()
                        .nameFIO(rawStudent.getNameFIO())
                        .code(rawStudent.getCode())
                        .studentNumber(rawStudent.getStudentNumber())
                        .className(className)
                        .subject(subject)
                        .date(date)
                        .school(school)
                        .schoolYear(DateNormalizerUtil.calculateSchoolYear(date))
                        .build();

                students.add(student);
            }
        }

        return students;
    }

    private void validateMetadata(String fileName, String date, String className, String subject) {
        List<String> missing = new ArrayList<>();

        if (!DateNormalizerUtil.isValidDate(date)) {
            missing.add("дата");
        }
        if (className == null || className.trim().isEmpty()) {
            missing.add("класс");
        }
        if (subject == null || subject.trim().isEmpty()) {
            missing.add("предмет");
        }

        if (!missing.isEmpty()) {
            throw new ProcessingException(
                    "Файл списка участников не прошел валидацию: " +
                            String.join(", ", missing) + " (" + fileName + ")"
            );
        }
    }

    /**
     * Извлекает данные по фиксированному порядку строк
     */
    private Map<String, String> extractMetadataFromText(String text) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("school", "");
        metadata.put("className", "");
        metadata.put("subject", "");

        String[] lines = text.split("\n");
        List<String> cleanLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleanLines.add(trimmed);
            }
        }

        for (int i = 0; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);

            if (line.contains("ГБОУ Школа №")) {
                metadata.put("school", line);

                for (int j = i + 1; j < Math.min(i + 8, cleanLines.size()); j++) {
                    String nextLine = cleanLines.get(j);

                    if (nextLine.contains("Оценка качества образования")
                            || nextLine.startsWith("Диагностика ")) {
                        continue;
                    }

                    String className = extractClassFromPdfLine(nextLine);

                    if (!className.isEmpty()) {
                        metadata.put("className", normalizeClass(className));

                        String subject = findSubjectAfterLine(cleanLines, j);
                        if (!subject.isEmpty()) {
                            metadata.put("subject", SubjectNormalizerUtil.normalize(subject));
                        }

                        break;
                    }
                }

                break;
            }
        }

        return metadata;
    }

    private String extractClassFromPdfLine(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }

        String normalized = line
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("\\s+", " ")
                .trim();

        // Нормальный старый формат:
        // Класс: 4-А
        Pattern classAfterPattern = Pattern.compile("Класс:\\s*(\\d{1,2}\\s*-?\\s*[А-Яа-яЁё])");
        Matcher classAfterMatcher = classAfterPattern.matcher(normalized);
        if (classAfterMatcher.find()) {
            return classAfterMatcher.group(1);
        }

        // Формат:
        // 8-АКласс:
        // 9-ЦКласс:
        Pattern classBeforeWithDashPattern = Pattern.compile("^(\\d{1,2})\\s*-\\s*([А-Яа-яЁё])\\s*Класс:");
        Matcher classBeforeWithDashMatcher = classBeforeWithDashPattern.matcher(normalized);
        if (classBeforeWithDashMatcher.find()) {
            return classBeforeWithDashMatcher.group(1) + "-" + classBeforeWithDashMatcher.group(2);
        }

        // Формат:
        // 4 АКласс:9116Код ОО:
        // 4 ЮКласс:9116Код ОО:
        // 4А Класс:
        // 4АКласс:
        Pattern classBeforeNoDashPattern = Pattern.compile("^(\\d{1,2})\\s*([А-Яа-яЁё])\\s*Класс:");
        Matcher classBeforeNoDashMatcher = classBeforeNoDashPattern.matcher(normalized);
        if (classBeforeNoDashMatcher.find()) {
            return classBeforeNoDashMatcher.group(1) + "-" + classBeforeNoDashMatcher.group(2);
        }

        // Нормальный новый формат:
        // Группа: 4-Б
        Pattern groupAfterPattern = Pattern.compile("Группа:\\s*(\\d{1,2}\\s*-?\\s*[А-Яа-яЁё])");
        Matcher groupAfterMatcher = groupAfterPattern.matcher(normalized);
        if (groupAfterMatcher.find()) {
            return groupAfterMatcher.group(1);
        }

        // Формат:
        // 4 БГруппа:9116Код ОО:
        // 4 ГГруппа:9116Код ОО:
        Pattern groupBeforePattern = Pattern.compile("^(\\d{1,2})\\s*([А-Яа-яЁё])\\s*Группа");
        Matcher groupBeforeMatcher = groupBeforePattern.matcher(normalized);
        if (groupBeforeMatcher.find()) {
            return groupBeforeMatcher.group(1) + "-" + groupBeforeMatcher.group(2);
        }

        return "";
    }

    private String findSubjectAfterLine(List<String> cleanLines, int lineIndex) {
        for (int i = lineIndex + 1; i < Math.min(lineIndex + 5, cleanLines.size()); i++) {
            String candidate = cleanLines.get(i).trim();

            if (candidate.isEmpty()
                    || candidate.contains("ФИО")
                    || candidate.contains("Код")
                    || candidate.contains("Номер")
                    || candidate.contains("Основной список")
                    || candidate.contains("Резервный список")) {
                continue;
            }

            String cleaned = trimSubjectAfterDistrict(candidate);
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }

        return "";
    }

    /**
     * Явно отрезает от предмета блок "Округ: ..." и всё, что идет после него.
     */
    private String trimSubjectAfterDistrict(String subjectLine) {
        if (subjectLine == null) {
            return "";
        }

        String normalizedLine = subjectLine.replaceAll("[\\r\\n]+", " ").trim();
        int districtPosition = normalizedLine.toLowerCase(Locale.ROOT).indexOf("округ");
        if (districtPosition >= 0) {
            return normalizedLine.substring(0, districtPosition).trim();
        }
        return normalizedLine;
    }

    private String extractDateFromFileName(String fileName) {
        // Убираем расширение .pdf
        fileName = fileName.replace(".pdf", "").replace(".PDF", "");

        String[] parts = fileName.split("_");

        for (String part : parts) {
            if (part.matches(".*\\d+.*") && (
                    part.contains("янв") || part.contains("фев") || part.contains("мар") ||
                            part.contains("апр") || part.contains("мая") || part.contains("июн") ||
                            part.contains("июл") || part.contains("авг") || part.contains("сен") ||
                            part.contains("окт") || part.contains("ноя") || part.contains("дек") ||
                            part.contains("Янв") || part.contains("Фев") || part.contains("Мар") ||
                            part.contains("Апр") || part.contains("Май") || part.contains("Июн") ||
                            part.contains("Июл") || part.contains("Авг") || part.contains("Сен") ||
                            part.contains("Окт") || part.contains("Ноя") || part.contains("Дек"))) {
                return part;
            }

            if (part.matches("\\d{1,2}[-–—]\\d{1,2}.*")) {
                return part;
            }
        }

        return "дата не определена";
    }


    private String normalizeClass(String className) {
        if (className == null || className.isEmpty()) return "";

        String normalized = className.trim()
                .replace(" ", "")
                .replace("–", "-")
                .replace("—", "-")
                .replace("№", "")
                .replace("класс", "")
                .replace("Класс", "")
                .replace(":", "")
                .replace(".", "");

        if (!normalized.contains("-") && normalized.matches(".*\\d[А-Яа-яЁё].*")) {
            normalized = normalized.replaceAll("(\\d{1,2})([А-Яа-яЁё])", "$1-$2");
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    private List<ListStudentData> extractStudentsFromText(String text) {
        List<ListStudentData> students = extractStudentsSameLineCodeFormat(text);

        if (!students.isEmpty()) {
            return students;
        }

        students = extractStudentsSplitColumnsCodeFormat(text);

        if (!students.isEmpty()) {
            return students;
        }

        return extractStudentsNumberOnlyFormat(text);
    }

    private List<ListStudentData> extractStudentsSameLineCodeFormat(String text) {
        List<ListStudentData> students = new ArrayList<>();
        String[] lines = text.split("\n");

        boolean inStudentSection = false;
        int studentCounter = 0;

        for (String s : lines) {
            String line = s.trim();
            line = line.replaceAll("(?i)\\s+Бланк\\s*$", "").trim();

            if (line.contains("Резервный список")) {
                inStudentSection = false;
                continue;
            }

            if (line.contains("ФИО обучающегося") ||
                    line.contains("ФИО участника") ||
                    line.contains("ФИО учащегося") ||
                    (line.contains("ФИО") && line.contains("Код")) ||
                    (line.contains("ФИО") && line.contains("Индивидуальный"))) {
                inStudentSection = true;
                continue;
            }

            if (inStudentSection) {
                if (line.isEmpty() ||
                        line.equalsIgnoreCase("Код") ||
                        line.equalsIgnoreCase("код") ||
                        line.equalsIgnoreCase("участника") ||
                        line.equalsIgnoreCase("обучающегося") ||
                        line.equalsIgnoreCase("Индивидуальный") ||
                        line.equalsIgnoreCase("Индивидуальный код") ||
                        line.equalsIgnoreCase("Код участника") ||
                        line.equalsIgnoreCase("Номер учащегося") ||
                        (line.contains("ФИО") && line.contains("Код")) ||
                        line.equalsIgnoreCase("Код диагностики") ||
                        line.toLowerCase(Locale.ROOT).contains("код диагностики") ||
                        line.equalsIgnoreCase("№ уч.") ||
                        line.equalsIgnoreCase("№ уч")) {
                    continue;
                }

                Pattern codePattern = Pattern.compile("(\\d{4}-\\d{4}[iI]?|\\d{1,4})$");
                Matcher matcher = codePattern.matcher(line);

                if (matcher.find()) {
                    String code = matcher.group(1);
                    String name = normalizeStudentName(line.substring(0, matcher.start()).trim());

                    if (isValidStudentName(name)) {
                        studentCounter++;

                        ListStudentData student = new ListStudentData();
                        student.setNameFIO(name);
                        student.setCode(code);
                        student.setStudentNumber(studentCounter);

                        students.add(student);
                    }
                }
            }
        }

        return students;
    }

    private List<ListStudentData> extractStudentsSplitColumnsCodeFormat(String text) {
        List<String> names = new ArrayList<>();
        List<String> codes = new ArrayList<>();

        String[] lines = text.split("\n");

        boolean readingNames = false;
        boolean readingCodes = false;

        for (String s : lines) {
            String line = s.trim();

            if (line.isEmpty()) {
                continue;
            }

            if (line.contains("Резервный список")) {
                readingNames = false;
                readingCodes = false;
                continue;
            }

            if (line.contains("ФИО обучающегося") ||
                    line.contains("ФИО участника") ||
                    line.contains("ФИО учащегося")) {
                readingNames = true;
                readingCodes = false;
                continue;
            }

            if (line.equalsIgnoreCase("Код") ||
                    line.equalsIgnoreCase("код") ||
                    line.equalsIgnoreCase("Код участника") ||
                    line.equalsIgnoreCase("Индивидуальный код") ||
                    line.contains("Код участника") ||
                    line.contains("Индивидуальный")) {
                readingNames = false;
                readingCodes = true;
                continue;
            }

            if (line.equalsIgnoreCase("участника") ||
                    line.equalsIgnoreCase("обучающегося")) {
                continue;
            }

            if (readingNames) {
                String name = normalizeStudentName(line);
                if (isValidStudentName(name)) {
                    names.add(name);
                }
                continue;
            }

            if (readingCodes) {
                Matcher codeMatcher = Pattern.compile("^(\\d{4}-\\d{4}|\\d{1,4})$").matcher(line);
                if (codeMatcher.find()) {
                    codes.add(codeMatcher.group(1));
                }
            }
        }

        if (names.isEmpty() || codes.isEmpty()) {
            return new ArrayList<>();
        }

        int count = Math.min(names.size(), codes.size());
        List<ListStudentData> students = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ListStudentData student = new ListStudentData();
            student.setNameFIO(names.get(i));
            student.setCode(codes.get(i));
            student.setStudentNumber(i + 1);
            students.add(student);
        }

        if (names.size() != codes.size()) {
            log.warn(
                    "Количество ФИО и кодов не совпадает: ФИО={}, кодов={}. Будет сохранено {} строк.",
                    names.size(),
                    codes.size(),
                    count
            );
        }

        return students;
    }

    private List<ListStudentData> extractStudentsNumberOnlyFormat(String text) {
        List<ListStudentData> students = new ArrayList<>();
        String[] lines = text.split("\n");

        boolean inMainStudentSection = false;

        for (String s : lines) {
            String line = s.trim();

            if (line.contains("Резервный список")) {
                inMainStudentSection = false;
                continue;
            }

            if (line.contains("Основной список")) {
                inMainStudentSection = true;
                continue;
            }

            if (!inMainStudentSection) {
                continue;
            }

            if (line.isEmpty()
                    || line.contains("ФИО")
                    || line.contains("Номер учащегося")) {
                continue;
            }

            Pattern rowPattern = Pattern.compile("^(.+?)\\s+(\\d{1,3})$");
            Matcher matcher = rowPattern.matcher(line);

            if (matcher.find()) {
                String name = normalizeStudentName(matcher.group(1).trim());
                String number = matcher.group(2);

                if (isValidStudentName(name)) {
                    ListStudentData student = new ListStudentData();
                    student.setNameFIO(name);
                    student.setStudentNumber(Integer.parseInt(number));
                    student.setCode("");
                    students.add(student);
                }
            }
        }

        return students;
    }

    private boolean isValidStudentName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String lower = name.toLowerCase(Locale.ROOT);

        return !lower.contains("фио")
                && !lower.contains("код")
                && !lower.contains("номер")
                && !lower.contains("основной список")
                && !lower.contains("резервный список")
                && name.contains(" ")
                && name.matches(".*[А-ЯЁЕ][а-яёе]+.*[А-ЯЁЕ][а-яёе]+.*");
    }

    private String normalizeStudentName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName
                .replace('ѐ', 'ё')
                .replace('Ѐ', 'Ё')
                .replaceAll("\\s+", " ")
                .trim();
    }
}