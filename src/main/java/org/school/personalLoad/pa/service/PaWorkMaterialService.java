package org.school.personalLoad.pa.service;

import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.*;
import org.school.personalLoad.pa.repository.PaWorkMaterialRepository;
import org.school.personalLoad.pa.service.impl.PaStoragePath;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PaWorkMaterialService {

    public enum FileKind { TEXT, ANSWERS }

    private static final Pattern PARALLEL_PATTERN = Pattern.compile("^(1[01]|[1-9])");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "odt", "xls", "xlsx", "ppt", "pptx",
            "zip", "rar", "7z", "png", "jpg", "jpeg");

    private final PaWorkMaterialRepository materialRepository;
    private final CurriculumPlanEntryRepository curriculumRepository;
    private final Path storageRoot;

    public PaWorkMaterialService(PaWorkMaterialRepository materialRepository,
                                 CurriculumPlanEntryRepository curriculumRepository,
                                 @Value("${pa.materials.storage-directory:pa-materials}") String storageDirectory) {
        this.materialRepository = materialRepository;
        this.curriculumRepository = curriculumRepository;
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<PaDtos.WorkMaterialReferenceRow> references(String academicYear) {
        Map<String, PaDtos.WorkMaterialReferenceRow> rows = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CurriculumPlanEntry entry : curriculumRepository.findAllByAcademicYear(academicYear)) {
            if (entry.isDeprecated() || blank(entry.getSubjectName()).isBlank() || blank(entry.getClassName()).isBlank()) continue;
            Integer parallel = parallel(entry.getClassName());
            if (parallel == null) continue;
            String subject = clean(entry.getSubjectName());
            String className = clean(entry.getClassName()).toUpperCase(Locale.ROOT);
            rows.putIfAbsent(subject + "|" + normalize(className),
                    new PaDtos.WorkMaterialReferenceRow(subject, className, parallel));
        }
        return rows.values().stream()
                .sorted(Comparator.comparing(PaDtos.WorkMaterialReferenceRow::subjectName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PaDtos.WorkMaterialReferenceRow::parallel)
                        .thenComparing(PaDtos.WorkMaterialReferenceRow::className, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaDtos.WorkMaterialRow> materials(String academicYear) {
        return materialRepository.findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc(academicYear)
                .stream().map(this::toRow).toList();
    }

    @Transactional(readOnly = true)
    public List<PaDtos.PublicWorkMaterialRow> publicMaterials(String academicYear) {
        return materials(academicYear).stream().map(row -> new PaDtos.PublicWorkMaterialRow(
                row.id(), row.academicYear(), row.subjectName(), row.scopeType(), row.scopeValue(), row.parallel(),
                row.level(), row.workType(), row.variantCount(), row.textAvailable(), row.textFileName(),
                row.answersAvailable(), row.answersFileName(), row.updatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<String> academicYears() {
        return materialRepository.findDistinctAcademicYears();
    }

    @Transactional
    public PaDtos.WorkMaterialUploadResponse upload(String academicYear,
                                                    String subjectName,
                                                    PaScopeType scopeType,
                                                    String scopeValue,
                                                    PaLevel level,
                                                    PaWorkType workType,
                                                    int variantCount,
                                                    MultipartFile textFile,
                                                    MultipartFile answersFile,
                                                    String username,
                                                    String uploaderFio) throws IOException {
        String year = clean(academicYear);
        String subject = clean(subjectName);
        if (year.isBlank()) throw new IllegalArgumentException("Не указан учебный год");
        if (subject.isBlank()) throw new IllegalArgumentException("Выберите предмет");
        if (scopeType == null) throw new IllegalArgumentException("Выберите параллель или конкретный класс");
        if (level == null) throw new IllegalArgumentException("Выберите уровень работы");
        if (workType == null) throw new IllegalArgumentException("Выберите тип работы");
        if (variantCount < 1 || variantCount > 99) throw new IllegalArgumentException("Количество вариантов должно быть от 1 до 99");
        boolean hasText = present(textFile);
        boolean hasAnswers = present(answersFile);
        if (!hasText && !hasAnswers) throw new IllegalArgumentException("Выберите файл с текстом работы или файл с ответами");
        validateFile(textFile, "текста работы");
        validateFile(answersFile, "ответов");

        String normalizedScope = normalizeScope(scopeType, scopeValue);
        PaWorkMaterial material = materialRepository
                .findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeAndVariantCountOrderByUpdatedAtDesc(
                        year, subject, scopeType, normalizedScope, level, workType, variantCount)
                .orElseGet(PaWorkMaterial::new);
        LocalDateTime now = LocalDateTime.now();
        if (material.getId() == null) {
            material.setAcademicYear(year);
            material.setSubjectName(subject);
            material.setScopeType(scopeType);
            material.setScopeValue(normalizedScope);
            material.setLevel(level);
            material.setWorkType(workType);
            material.setVariantCount(variantCount);
            material.setCreatedAt(now);
            material.setUpdatedAt(now);
            material = materialRepository.saveAndFlush(material);
        }

        Path directory = yearDirectory(year);
        Files.createDirectories(directory);
        if (hasText) {
            String oldStoredName = material.getTextStoredFileName();
            String storedName = store(directory, material.getId(), "text", textFile);
            material.setTextOriginalFileName(originalName(textFile));
            material.setTextStoredFileName(storedName);
            material.setTextUploadedByUsername(clean(username));
            material.setTextUploadedByFio(displayUploader(uploaderFio, username));
            material.setTextUploadedAt(now);
            deleteReplacedFile(directory, oldStoredName, storedName);
        }
        if (hasAnswers) {
            String oldStoredName = material.getAnswersStoredFileName();
            String storedName = store(directory, material.getId(), "answers", answersFile);
            material.setAnswersOriginalFileName(originalName(answersFile));
            material.setAnswersStoredFileName(storedName);
            material.setAnswersUploadedByUsername(clean(username));
            material.setAnswersUploadedByFio(displayUploader(uploaderFio, username));
            material.setAnswersUploadedAt(now);
            deleteReplacedFile(directory, oldStoredName, storedName);
        }
        material.setUpdatedAt(now);
        material = materialRepository.saveAndFlush(material);
        String message = hasText && hasAnswers
                ? "Текст работы и ответы загружены"
                : hasText ? "Текст работы загружен" : "Ответы загружены";
        return new PaDtos.WorkMaterialUploadResponse(material.getId(), hasText, hasAnswers, message);
    }

    @Transactional(readOnly = true)
    public byte[] loadFile(Long materialId, FileKind kind) throws IOException {
        PaWorkMaterial material = requireMaterial(materialId);
        String storedName = kind == FileKind.TEXT ? material.getTextStoredFileName() : material.getAnswersStoredFileName();
        if (blank(storedName).isBlank()) {
            throw new IllegalArgumentException(kind == FileKind.TEXT ? "Текст работы не загружен" : "Ответы не загружены");
        }
        Path path = PaStoragePath.resolveUploadedFile(yearDirectory(material.getAcademicYear()), storedName);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Файл не найден на сервере");
        return Files.readAllBytes(path);
    }

    @Transactional(readOnly = true)
    public String fileName(Long materialId, FileKind kind) {
        PaWorkMaterial material = requireMaterial(materialId);
        String fileName = kind == FileKind.TEXT ? material.getTextOriginalFileName() : material.getAnswersOriginalFileName();
        return blank(fileName).isBlank()
                ? (kind == FileKind.TEXT ? "Текст_работы" : "Ответы") + "_" + materialId
                : fileName;
    }

    @Transactional(readOnly = true)
    public byte[] downloadAll(String academicYear) throws IOException {
        List<PaWorkMaterial> materials = materialRepository
                .findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc(academicYear);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Set<String> entryNames = new HashSet<>();
            for (PaWorkMaterial material : materials) {
                addToZip(zip, entryNames, material, FileKind.TEXT);
                addToZip(zip, entryNames, material, FileKind.ANSWERS);
            }
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private void addToZip(ZipOutputStream zip,
                          Set<String> entryNames,
                          PaWorkMaterial material,
                          FileKind kind) throws IOException {
        String stored = kind == FileKind.TEXT ? material.getTextStoredFileName() : material.getAnswersStoredFileName();
        String original = kind == FileKind.TEXT ? material.getTextOriginalFileName() : material.getAnswersOriginalFileName();
        if (blank(stored).isBlank()) return;
        Path path = PaStoragePath.resolveUploadedFile(yearDirectory(material.getAcademicYear()), stored);
        if (!Files.isRegularFile(path)) return;
        String folder = zipSegment(material.getSubjectName()) + "/" + zipSegment(material.getScopeValue()) + "/"
                + levelLabel(material.getLevel()) + "/" + workTypeLabel(material.getWorkType()) + "/";
        String role = kind == FileKind.TEXT ? "Текст_" : "Ответы_";
        String base = folder + role + zipSegment(original);
        String unique = base;
        int suffix = 2;
        while (!entryNames.add(unique.toLowerCase(Locale.ROOT))) {
            unique = folder + material.getId() + "_" + suffix++ + "_" + role + zipSegment(original);
        }
        zip.putNextEntry(new ZipEntry(unique));
        Files.copy(path, zip);
        zip.closeEntry();
    }

    private PaDtos.WorkMaterialRow toRow(PaWorkMaterial material) {
        boolean textAvailable = storedFileExists(material.getAcademicYear(), material.getTextStoredFileName());
        boolean answersAvailable = storedFileExists(material.getAcademicYear(), material.getAnswersStoredFileName());
        return new PaDtos.WorkMaterialRow(material.getId(), material.getAcademicYear(), material.getSubjectName(),
                material.getScopeType(), material.getScopeValue(), parallel(material.getScopeValue()), material.getLevel(),
                material.getWorkType(), material.getVariantCount(), textAvailable, material.getTextOriginalFileName(),
                material.getTextUploadedByFio(), material.getTextUploadedAt(), answersAvailable,
                material.getAnswersOriginalFileName(), material.getAnswersUploadedByFio(),
                material.getAnswersUploadedAt(), material.getUpdatedAt());
    }

    private PaWorkMaterial requireMaterial(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Материал ПА не найден: " + id));
    }

    private String normalizeScope(PaScopeType type, String value) {
        String scope = clean(value).toUpperCase(Locale.ROOT).replace('Ё', 'Е');
        Integer number = parallel(scope);
        if (number == null) throw new IllegalArgumentException("Не удалось определить параллель или класс");
        if (type == PaScopeType.PARALLEL) return String.valueOf(number);
        if (!scope.matches("^(1[01]|[1-9])[\\s\\-–—_./]*[А-ЯA-Z]+$")) {
            throw new IllegalArgumentException("Для конкретного класса укажите номер и букву, например 7-А");
        }
        return scope.replaceAll("[\\s–—_./]+", "-").replaceAll("-+", "-");
    }

    private String store(Path directory, Long id, String role, MultipartFile file) throws IOException {
        String storedName = id + "_" + role + "_" + UUID.randomUUID() + "_" + originalName(file);
        Path target = PaStoragePath.resolveUploadedFile(directory, storedName);
        try (var input = file.getInputStream()) {
            Files.copy(input, target);
        }
        return target.getFileName().toString();
    }

    private void deleteReplacedFile(Path directory, String oldStoredName, String newStoredName) throws IOException {
        if (blank(oldStoredName).isBlank() || Objects.equals(oldStoredName, newStoredName)) return;
        Files.deleteIfExists(PaStoragePath.resolveUploadedFile(directory, oldStoredName));
    }

    private void validateFile(MultipartFile file, String label) {
        if (!present(file)) return;
        String name = originalName(file);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Недопустимый формат файла " + label + ": " + name);
        }
    }

    private boolean storedFileExists(String year, String storedName) {
        if (blank(storedName).isBlank()) return false;
        try {
            return Files.isRegularFile(PaStoragePath.resolveUploadedFile(yearDirectory(year), storedName));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Path yearDirectory(String academicYear) {
        return storageRoot.resolve(clean(academicYear).replace('/', '-')).normalize();
    }

    private Integer parallel(String value) {
        Matcher matcher = PARALLEL_PATTERN.matcher(clean(value));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private boolean present(MultipartFile file) { return file != null && !file.isEmpty(); }
    private String originalName(MultipartFile file) {
        String name = file == null ? "" : blank(file.getOriginalFilename()).replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return clean(slash >= 0 ? name.substring(slash + 1) : name);
    }
    private String displayUploader(String fio, String username) {
        return clean(fio).isBlank() ? (clean(username).isBlank() ? "Неизвестный пользователь" : clean(username)) : clean(fio);
    }
    private String zipSegment(String value) {
        String result = clean(value).replaceAll("[\\p{Cntrl}/:*?\"<>|]", "_");
        return result.isBlank() ? "Без_названия" : result;
    }
    private String levelLabel(PaLevel value) { return value == PaLevel.ADVANCED ? "Углубленный" : "Базовый"; }
    private String workTypeLabel(PaWorkType value) {
        if (value == PaWorkType.ENTRY) return "Входная";
        if (value == PaWorkType.MID) return "Промежуточная";
        return "Выходная";
    }
    private String normalize(String value) { return clean(value).toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", ""); }
    private String clean(String value) { return blank(value).replaceAll("\\s+", " "); }
    private String blank(String value) { return value == null ? "" : value.trim(); }
}
