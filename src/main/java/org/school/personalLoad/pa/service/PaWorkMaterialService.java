package org.school.personalLoad.pa.service;

import org.school.personalLoad.model.CurriculumPlanEntry;
import org.school.personalLoad.pa.dto.PaDtos;
import org.school.personalLoad.pa.model.*;
import org.school.personalLoad.pa.repository.PaWorkMaterialFileRepository;
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
    private final PaWorkMaterialFileRepository fileRepository;
    private final CurriculumPlanEntryRepository curriculumRepository;
    private final Path storageRoot;

    public PaWorkMaterialService(PaWorkMaterialRepository materialRepository,
                                 PaWorkMaterialFileRepository fileRepository,
                                 CurriculumPlanEntryRepository curriculumRepository,
                                 @Value("${pa.materials.storage-directory:pa-materials}") String storageDirectory) {
        this.materialRepository = materialRepository;
        this.fileRepository = fileRepository;
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
        List<PaWorkMaterial> materials = materialRepository
                .findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc(academicYear);
        Map<Long, List<PaWorkMaterialFile>> filesByMaterial = filesByMaterial(materials);
        return materials.stream()
                .map(material -> toRow(material, filesByMaterial.getOrDefault(material.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaDtos.PublicWorkMaterialRow> publicMaterials(String academicYear) {
        return materials(academicYear).stream().map(row -> new PaDtos.PublicWorkMaterialRow(
                row.id(), row.academicYear(), row.subjectName(), row.scopeType(), row.scopeValue(), row.parallel(),
                row.level(), row.workType(), row.variantCount(), publicFiles(row.textFiles()), publicFiles(row.answerFiles()),
                row.updatedAt())).toList();
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
                                                    List<MultipartFile> textFiles,
                                                    List<MultipartFile> answerFiles,
                                                    String username,
                                                    String uploaderFio) throws IOException {
        String year = clean(academicYear);
        String subject = clean(subjectName);
        if (year.isBlank()) throw new IllegalArgumentException("Не указан учебный год");
        if (subject.isBlank()) throw new IllegalArgumentException("Выберите предмет");
        if (scopeType == null) throw new IllegalArgumentException("Выберите параллель или конкретный класс");
        if (level == null) throw new IllegalArgumentException("Выберите уровень работы");
        if (workType == null) throw new IllegalArgumentException("Выберите тип работы");
        if (variantCount < 1 || variantCount > 999) {
            throw new IllegalArgumentException("Количество вариантов должно быть от 1 до 999");
        }
        List<MultipartFile> texts = nonEmptyFiles(textFiles);
        List<MultipartFile> answers = nonEmptyFiles(answerFiles);
        if (texts.isEmpty() && answers.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один файл с текстом работы или ответами");
        }
        texts.forEach(file -> validateFile(file, "текста работы"));
        answers.forEach(file -> validateFile(file, "ответов"));

        String normalizedScope = normalizeScope(scopeType, scopeValue);
        PaWorkMaterial material = materialRepository
                .findFirstByAcademicYearAndSubjectNameAndScopeTypeAndScopeValueAndLevelAndWorkTypeOrderByUpdatedAtDesc(
                        year, subject, scopeType, normalizedScope, level, workType)
                .orElseGet(PaWorkMaterial::new);
        LocalDateTime now = LocalDateTime.now();
        material.setVariantCount(variantCount);
        if (material.getId() == null) {
            material.setAcademicYear(year);
            material.setSubjectName(subject);
            material.setScopeType(scopeType);
            material.setScopeValue(normalizedScope);
            material.setLevel(level);
            material.setWorkType(workType);
            material.setCreatedAt(now);
            material.setUpdatedAt(now);
            material = materialRepository.saveAndFlush(material);
        }

        Path directory = yearDirectory(year);
        Files.createDirectories(directory);
        migrateLegacyFiles(material);
        for (MultipartFile file : texts) {
            storeAttachment(material, PaWorkMaterialFileKind.TEXT, file, username, uploaderFio, now, directory);
        }
        for (MultipartFile file : answers) {
            storeAttachment(material, PaWorkMaterialFileKind.ANSWERS, file, username, uploaderFio, now, directory);
        }
        material.setUpdatedAt(now);
        material = materialRepository.saveAndFlush(material);
        String message = "Загружено файлов: " + (texts.size() + answers.size())
                + " (тексты: " + texts.size() + ", ответы: " + answers.size() + ")";
        return new PaDtos.WorkMaterialUploadResponse(material.getId(), texts.size(), answers.size(), message);
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
    public byte[] loadAttachment(Long fileId) throws IOException {
        PaWorkMaterialFile file = requireAttachment(fileId);
        Path path = PaStoragePath.resolveUploadedFile(
                yearDirectory(file.getMaterial().getAcademicYear()), file.getStoredFileName());
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Файл не найден на сервере");
        return Files.readAllBytes(path);
    }

    @Transactional(readOnly = true)
    public String attachmentFileName(Long fileId) {
        return requireAttachment(fileId).getOriginalFileName();
    }

    @Transactional(readOnly = true)
    public byte[] downloadAll(String academicYear) throws IOException {
        List<PaWorkMaterial> materials = materialRepository
                .findAllByAcademicYearOrderBySubjectNameAscScopeValueAscLevelAscWorkTypeAsc(academicYear);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Set<String> entryNames = new HashSet<>();
            Map<Long, List<PaWorkMaterialFile>> filesByMaterial = filesByMaterial(materials);
            for (PaWorkMaterial material : materials) {
                List<PaWorkMaterialFile> files = filesByMaterial.getOrDefault(material.getId(), List.of());
                for (PaWorkMaterialFile file : files) addToZip(zip, entryNames, material, file);
                if (files.stream().noneMatch(file -> file.getKind() == PaWorkMaterialFileKind.TEXT)) {
                    addLegacyToZip(zip, entryNames, material, FileKind.TEXT);
                }
                if (files.stream().noneMatch(file -> file.getKind() == PaWorkMaterialFileKind.ANSWERS)) {
                    addLegacyToZip(zip, entryNames, material, FileKind.ANSWERS);
                }
            }
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private void addLegacyToZip(ZipOutputStream zip,
                                Set<String> entryNames,
                                PaWorkMaterial material,
                                FileKind kind) throws IOException {
        String stored = kind == FileKind.TEXT ? material.getTextStoredFileName() : material.getAnswersStoredFileName();
        String original = kind == FileKind.TEXT ? material.getTextOriginalFileName() : material.getAnswersOriginalFileName();
        if (blank(stored).isBlank()) return;
        Path path = PaStoragePath.resolveUploadedFile(yearDirectory(material.getAcademicYear()), stored);
        if (!Files.isRegularFile(path)) return;
        addPathToZip(zip, entryNames, material,
                kind == FileKind.TEXT ? PaWorkMaterialFileKind.TEXT : PaWorkMaterialFileKind.ANSWERS,
                original, path);
    }

    private void addToZip(ZipOutputStream zip,
                          Set<String> entryNames,
                          PaWorkMaterial material,
                          PaWorkMaterialFile file) throws IOException {
        Path path = PaStoragePath.resolveUploadedFile(yearDirectory(material.getAcademicYear()), file.getStoredFileName());
        if (!Files.isRegularFile(path)) return;
        addPathToZip(zip, entryNames, material, file.getKind(), file.getOriginalFileName(), path);
    }

    private void addPathToZip(ZipOutputStream zip,
                              Set<String> entryNames,
                              PaWorkMaterial material,
                              PaWorkMaterialFileKind kind,
                              String original,
                              Path path) throws IOException {
        String folder = zipSegment(material.getSubjectName()) + "/" + zipSegment(material.getScopeValue()) + "/"
                + levelLabel(material.getLevel()) + "/" + workTypeLabel(material.getWorkType()) + "/";
        String role = kind == PaWorkMaterialFileKind.TEXT ? "Текст_" : "Ответы_";
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

    private PaDtos.WorkMaterialRow toRow(PaWorkMaterial material, List<PaWorkMaterialFile> files) {
        List<PaDtos.WorkMaterialFileRow> textFiles = files.stream()
                .filter(file -> file.getKind() == PaWorkMaterialFileKind.TEXT)
                .filter(file -> storedFileExists(material.getAcademicYear(), file.getStoredFileName()))
                .map(this::toFileRow)
                .toList();
        List<PaDtos.WorkMaterialFileRow> answerFiles = files.stream()
                .filter(file -> file.getKind() == PaWorkMaterialFileKind.ANSWERS)
                .filter(file -> storedFileExists(material.getAcademicYear(), file.getStoredFileName()))
                .map(this::toFileRow)
                .toList();
        if (textFiles.isEmpty() && storedFileExists(material.getAcademicYear(), material.getTextStoredFileName())) {
            textFiles = List.of(legacyFileRow(material, PaWorkMaterialFileKind.TEXT));
        }
        if (answerFiles.isEmpty() && storedFileExists(material.getAcademicYear(), material.getAnswersStoredFileName())) {
            answerFiles = List.of(legacyFileRow(material, PaWorkMaterialFileKind.ANSWERS));
        }
        return new PaDtos.WorkMaterialRow(material.getId(), material.getAcademicYear(), material.getSubjectName(),
                material.getScopeType(), material.getScopeValue(), parallel(material.getScopeValue()), material.getLevel(),
                material.getWorkType(), material.getVariantCount(), textFiles, answerFiles, material.getUpdatedAt());
    }

    private PaWorkMaterial requireMaterial(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Материал ПА не найден: " + id));
    }

    private PaWorkMaterialFile requireAttachment(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Файл материала ПА не найден: " + id));
    }

    private Map<Long, List<PaWorkMaterialFile>> filesByMaterial(List<PaWorkMaterial> materials) {
        List<Long> ids = materials.stream().map(PaWorkMaterial::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, List<PaWorkMaterialFile>> result = new HashMap<>();
        for (PaWorkMaterialFile file : fileRepository
                .findAllByMaterialIdInOrderByMaterialIdAscKindAscOriginalFileNameAscIdAsc(ids)) {
            result.computeIfAbsent(file.getMaterial().getId(), ignored -> new ArrayList<>()).add(file);
        }
        return result;
    }

    private List<PaDtos.PublicWorkMaterialFileRow> publicFiles(List<PaDtos.WorkMaterialFileRow> files) {
        return files.stream().map(file -> new PaDtos.PublicWorkMaterialFileRow(
                file.id(), file.kind(), file.fileName(), file.legacy())).toList();
    }

    private PaDtos.WorkMaterialFileRow toFileRow(PaWorkMaterialFile file) {
        return new PaDtos.WorkMaterialFileRow(file.getId(), file.getKind(), file.getOriginalFileName(),
                file.getUploadedByFio(), file.getUploadedAt(), false);
    }

    private PaDtos.WorkMaterialFileRow legacyFileRow(PaWorkMaterial material, PaWorkMaterialFileKind kind) {
        boolean text = kind == PaWorkMaterialFileKind.TEXT;
        return new PaDtos.WorkMaterialFileRow(null, kind,
                text ? material.getTextOriginalFileName() : material.getAnswersOriginalFileName(),
                text ? material.getTextUploadedByFio() : material.getAnswersUploadedByFio(),
                text ? material.getTextUploadedAt() : material.getAnswersUploadedAt(), true);
    }

    private void migrateLegacyFiles(PaWorkMaterial material) {
        migrateLegacyFile(material, PaWorkMaterialFileKind.TEXT, material.getTextOriginalFileName(),
                material.getTextStoredFileName(), material.getTextUploadedByUsername(),
                material.getTextUploadedByFio(), material.getTextUploadedAt());
        migrateLegacyFile(material, PaWorkMaterialFileKind.ANSWERS, material.getAnswersOriginalFileName(),
                material.getAnswersStoredFileName(), material.getAnswersUploadedByUsername(),
                material.getAnswersUploadedByFio(), material.getAnswersUploadedAt());
    }

    private void migrateLegacyFile(PaWorkMaterial material,
                                   PaWorkMaterialFileKind kind,
                                   String originalName,
                                   String storedName,
                                   String username,
                                   String uploaderFio,
                                   LocalDateTime uploadedAt) {
        if (blank(storedName).isBlank() || !storedFileExists(material.getAcademicYear(), storedName)) return;
        String name = blank(originalName).isBlank() ? (kind == PaWorkMaterialFileKind.TEXT ? "Текст работы" : "Ответы") : originalName;
        if (fileRepository.findFirstByMaterialIdAndKindAndOriginalFileNameIgnoreCaseOrderByIdDesc(
                material.getId(), kind, name).isPresent()) return;
        PaWorkMaterialFile file = new PaWorkMaterialFile();
        file.setMaterial(material);
        file.setKind(kind);
        file.setOriginalFileName(name);
        file.setStoredFileName(storedName);
        file.setUploadedByUsername(clean(username).isBlank() ? "unknown" : clean(username));
        file.setUploadedByFio(displayUploader(uploaderFio, username));
        file.setUploadedAt(uploadedAt == null
                ? (material.getCreatedAt() == null ? LocalDateTime.now() : material.getCreatedAt())
                : uploadedAt);
        fileRepository.saveAndFlush(file);
    }

    private void storeAttachment(PaWorkMaterial material,
                                 PaWorkMaterialFileKind kind,
                                 MultipartFile upload,
                                 String username,
                                 String uploaderFio,
                                 LocalDateTime uploadedAt,
                                 Path directory) throws IOException {
        String original = originalName(upload);
        Optional<PaWorkMaterialFile> existing = fileRepository
                .findFirstByMaterialIdAndKindAndOriginalFileNameIgnoreCaseOrderByIdDesc(
                        material.getId(), kind, original);
        PaWorkMaterialFile attachment = existing.orElseGet(PaWorkMaterialFile::new);
        String oldStoredName = attachment.getStoredFileName();
        String storedName = store(directory, material.getId(),
                kind == PaWorkMaterialFileKind.TEXT ? "text" : "answers", upload);
        attachment.setMaterial(material);
        attachment.setKind(kind);
        attachment.setOriginalFileName(original);
        attachment.setStoredFileName(storedName);
        attachment.setUploadedByUsername(clean(username).isBlank() ? "unknown" : clean(username));
        attachment.setUploadedByFio(displayUploader(uploaderFio, username));
        attachment.setUploadedAt(uploadedAt);
        fileRepository.saveAndFlush(attachment);
        deleteReplacedFile(directory, oldStoredName, storedName);
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

    private List<MultipartFile> nonEmptyFiles(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream().filter(this::present).toList();
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
