package org.school.personalLoad.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.school.personalLoad.dao.NamingMeshDAO;
import org.school.personalLoad.dao.TarifficationChangesDAO;
import org.school.personalLoad.dao.TarifficationPersonDAO;
import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.service.NamingMeshService;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.school.personalLoad.model.TarifficationChanges.ChangeType.MESH_MAPPING_CHANGED;


public class NamingMeshServiceImpl implements NamingMeshService {

    private final NamingMeshDAO namingMeshDAO;
    private final TarifficationChangesDAO changesDAO;
    private final TarifficationPersonDAO personDAO;

    public NamingMeshServiceImpl() {
        this.namingMeshDAO = new NamingMeshDAO();
        this.changesDAO = new TarifficationChangesDAO();
        this.personDAO = new TarifficationPersonDAO();
    }

    @Override
    public List<TarifficationChanges> processNamingMeshFile(String filePath) {
        List<TarifficationChanges> changes = new ArrayList<>();

        try {
            System.out.println("📖 Начинаем обработку файла naming mesh: " + filePath);

            // 1. Читаем данные из файла
            List<NamingMesh> newNamingMeshes = readNamingMeshFromExcel(filePath);
            System.out.println("📊 Прочитано записей из файла: " + newNamingMeshes.size());

            // 2. Получаем текущие данные из БД
            List<NamingMesh> currentNamingMeshes = namingMeshDAO.findAll();
            System.out.println("💾 Текущих записей в БД: " + currentNamingMeshes.size());

            // 3. Сравниваем и находим изменения
            changes = compareNamingMeshes(currentNamingMeshes, newNamingMeshes);

            // 4. Сохраняем новые данные
            if (!newNamingMeshes.isEmpty()) {
                namingMeshDAO.saveAll(newNamingMeshes);
                System.out.println("💾 Сохранено записей naming mesh: " + newNamingMeshes.size());
            }

            // 5. Сохраняем изменения в историю
            if (!changes.isEmpty()) {
                changesDAO.saveAll(changes);
                System.out.println("📝 Сохранено записей изменений: " + changes.size());
            }

            // 6. Обновляем связи в существующих записях тарификации
            updateNamingMeshRelations();

        } catch (Exception e) {
            System.err.println("❌ Ошибка при обработке файла: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Ошибка обработки файла naming mesh", e);
        }

        return changes;
    }

    @Override
    public List<NamingMesh> getAllNamingMeshes() {
        return namingMeshDAO.findAll();
    }

    @Override
    public Optional<NamingMesh> findNamingMesh(String subjectName, String className, String groupNameEducationalPlan) {
        return namingMeshDAO.findById(subjectName, className, groupNameEducationalPlan);
    }

    @Override
    public void clearAllNamingMeshes() {
        namingMeshDAO.deleteAll();
        System.out.println("🧹 Все записи naming mesh очищены");
    }

    @Override
    public void saveNamingMeshes(List<NamingMesh> namingMeshes) {
        if (namingMeshes != null && !namingMeshes.isEmpty()) {
            namingMeshDAO.saveAll(namingMeshes);
            System.out.println("💾 Сохранено записей naming mesh: " + namingMeshes.size());
            updateNamingMeshRelations();
        }
    }

    @Override
    public void updateNamingMeshRelations() {
        personDAO.updateAllNamingMeshRelations();
        System.out.println("🔗 Обновлены связи naming mesh в записях тарификации");
    }

    @Override
    public boolean existsNamingMesh(String subjectName, String className, String groupNameEducationalPlan) {
        return namingMeshDAO.findById(subjectName, className, groupNameEducationalPlan).isPresent();
    }

    @Override
    public String getClassNameMesh(String subjectName, String className, String groupNameEducationalPlan) {
        Optional<NamingMesh> namingMesh = findNamingMesh(subjectName, className, groupNameEducationalPlan);
        return namingMesh.map(NamingMesh::getClassNameMesh).orElse(className);
    }

    @Override
    public String getGroupNameMesh(String subjectName, String className, String groupNameEducationalPlan) {
        Optional<NamingMesh> namingMesh = findNamingMesh(subjectName, className, groupNameEducationalPlan);
        return namingMesh.map(NamingMesh::getGroupNameMesh).orElse(groupNameEducationalPlan);
    }

    @Override
    public long getNamingMeshCount() {
        return getAllNamingMeshes().size();
    }

    @Override
    public boolean deleteNamingMesh(String subjectName, String className, String groupNameEducationalPlan) {
        Optional<NamingMesh> existing = findNamingMesh(subjectName, className, groupNameEducationalPlan);
        if (existing.isPresent()) {
            try {
                // Используем DAO для удаления
                namingMeshDAO.delete(existing.get());

                // Создаем запись об изменении
                TarifficationChanges change = createMeshChangeRecord(
                        existing.get(),
                        TarifficationChanges.ChangeType.REMOVED,
                        "Удалена связь вручную"
                );
                changesDAO.saveAll(Collections.singletonList(change));

                // Обновляем связи
                updateNamingMeshRelations();

                System.out.println("🗑️ Удалена запись naming mesh: " + subjectName +
                        ", " + className + ", " + groupNameEducationalPlan);
                return true;

            } catch (Exception e) {
                System.err.println("❌ Ошибка при удалении naming mesh: " + e.getMessage());
                throw new RuntimeException("Ошибка при удалении naming mesh", e);
            }
        }
        return false;
    }

    @Override
    public boolean hasChanges(List<NamingMesh> newNamingMeshes) {
        if (newNamingMeshes == null) {
            return false;
        }

        List<NamingMesh> current = getAllNamingMeshes();

        // Проверка по количеству
        if (current.size() != newNamingMeshes.size()) {
            return true;
        }

        // Проверка по содержанию
        Map<String, NamingMesh> currentMap = createNamingMeshMap(current);
        Map<String, NamingMesh> newMap = createNamingMeshMap(newNamingMeshes);

        if (!currentMap.keySet().equals(newMap.keySet())) {
            return true;
        }

        // Проверка значений
        for (String key : currentMap.keySet()) {
            NamingMesh currentMesh = currentMap.get(key);
            NamingMesh newMesh = newMap.get(key);

            if (!isMeshEqual(currentMesh, newMesh)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Чтение данных из Excel файла
     */
    private List<NamingMesh> readNamingMeshFromExcel(String filePath) throws IOException {
        List<NamingMesh> namingMeshes = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet("Тарификация");
            if (sheet == null) {
                throw new IOException("Лист 'Тарификация' не найден в файле");
            }

            // Определяем индексы колонок
            Map<String, Integer> columnIndexes = findColumnIndexes(sheet);

            // Проверяем, что найдены все необходимые колонки
            if (!columnIndexes.containsKey("subject") || !columnIndexes.containsKey("className")) {
                throw new IOException("Не найдены обязательные колонки: 'Предмет' и 'Класс по УП'");
            }

            // Проходим по строкам (пропускаем заголовок)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                NamingMesh namingMesh = createNamingMeshFromRow(row, columnIndexes);
                if (namingMesh != null) {
                    namingMeshes.add(namingMesh);
                }
            }
        }

        return namingMeshes;
    }

    /**
     * Поиск индексов колонок
     */
    private Map<String, Integer> findColumnIndexes(Sheet sheet) {
        Map<String, Integer> indexes = new HashMap<>();
        Row headerRow = sheet.getRow(0);

        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String cellValue = getCellValueAsString(cell);
                if (cellValue != null) { // Добавляем проверку на null
                    cellValue = cellValue.trim(); // Теперь безопасно использовать trim()
                    switch (cellValue) {
                        case "Предмет":
                            indexes.put("subject", cell.getColumnIndex());
                            break;
                        case "Класс по УП":
                            indexes.put("className", cell.getColumnIndex());
                            break;
                        case "группа по УП":
                            indexes.put("groupName", cell.getColumnIndex());
                            break;
                        case "Класс по МЭШ":
                            indexes.put("classNameMesh", cell.getColumnIndex());
                            break;
                        case "группа по МЭШ":
                            indexes.put("groupNameMesh", cell.getColumnIndex());
                            break;
                    }
                }
            }
        }

        // Устанавливаем значения по умолчанию для отсутствующих колонок
        if (!indexes.containsKey("groupName")) {
            indexes.put("groupName", -1);
        }
        if (!indexes.containsKey("classNameMesh")) {
            indexes.put("classNameMesh", -1);
        }
        if (!indexes.containsKey("groupNameMesh")) {
            indexes.put("groupNameMesh", -1);
        }

        return indexes;
    }

    /**
     * Создание NamingMesh из строки Excel
     */
    private NamingMesh createNamingMeshFromRow(Row row, Map<String, Integer> columnIndexes) {
        String subjectName = getCellValue(row, columnIndexes.get("subject"));
        String className = getCellValue(row, columnIndexes.get("className"));
        String groupNameEducationalPlan = getCellValue(row, columnIndexes.get("groupName"));
        String classNameMesh = getCellValue(row, columnIndexes.get("classNameMesh"));
        String groupNameMesh = getCellValue(row, columnIndexes.get("groupNameMesh"));

        // Проверяем обязательные поля
        if (subjectName == null || subjectName.trim().isEmpty() ||
                className == null || className.trim().isEmpty()) {
            return null;
        }

        // Если поля МЭШ пустые, используем значения из УП
        if (classNameMesh == null || classNameMesh.trim().isEmpty()) {
            classNameMesh = className;
        }
        if (groupNameMesh == null || groupNameMesh.trim().isEmpty()) {
            groupNameMesh = groupNameEducationalPlan != null ? groupNameEducationalPlan : "";
        }
        if (groupNameEducationalPlan == null) {
            groupNameEducationalPlan = "";
        }

        return new NamingMesh(
                subjectName.trim(),
                className.trim(),
                groupNameEducationalPlan.trim(),
                groupNameMesh.trim(),
                classNameMesh.trim()
        );
    }

    /**
     * Сравнение старых и новых naming mesh
     */
    private List<TarifficationChanges> compareNamingMeshes(List<NamingMesh> current, List<NamingMesh> newMeshes) {
        List<TarifficationChanges> changes = new ArrayList<>();

        if (newMeshes == null) {
            return changes;
        }

        // Создаем мапы для быстрого поиска
        Map<String, NamingMesh> currentMap = createNamingMeshMap(current);
        Map<String, NamingMesh> newMap = createNamingMeshMap(newMeshes);

        // 1. Поиск удаленных записей
        for (NamingMesh currentMesh : current) {
            String key = createNamingMeshKey(currentMesh);
            if (!newMap.containsKey(key)) {
                changes.add(createMeshChangeRecord(currentMesh,
                        TarifficationChanges.ChangeType.REMOVED, "Удалена связь"));
            }
        }

        // 2. Поиск добавленных записей
        for (NamingMesh newMesh : newMeshes) {
            String key = createNamingMeshKey(newMesh);
            if (!currentMap.containsKey(key)) {
                changes.add(createMeshChangeRecord(newMesh,
                        TarifficationChanges.ChangeType.ADDED, "Добавлена новая связь"));
            }
        }

        // 3. Поиск измененных записей
        for (NamingMesh newMesh : newMeshes) {
            String key = createNamingMeshKey(newMesh);
            NamingMesh currentMesh = currentMap.get(key);

            if (currentMesh != null && !isMeshEqual(currentMesh, newMesh)) {
                String changeDescription = buildChangeDescription(currentMesh, newMesh);
                changes.add(createMeshChangeRecord(newMesh,
                        MESH_MAPPING_CHANGED, changeDescription));
            }
        }

        return changes;
    }

    /**
     * Создание ключа для NamingMesh
     */
    private String createNamingMeshKey(NamingMesh mesh) {
        return (mesh.getSubjectName() + "|" +
                mesh.getClassName() + "|" +
                mesh.getGroupNameEducationalPlan()).toLowerCase();
    }

    /**
     * Создание мапы для быстрого поиска
     */
    private Map<String, NamingMesh> createNamingMeshMap(List<NamingMesh> meshes) {
        Map<String, NamingMesh> map = new HashMap<>();
        for (NamingMesh mesh : meshes) {
            map.put(createNamingMeshKey(mesh), mesh);
        }
        return map;
    }

    /**
     * Проверка равенства двух NamingMesh
     */
    private boolean isMeshEqual(NamingMesh mesh1, NamingMesh mesh2) {
        return Objects.equals(mesh1.getGroupNameMesh(), mesh2.getGroupNameMesh()) &&
                Objects.equals(mesh1.getClassNameMesh(), mesh2.getClassNameMesh());
    }

    /**
     * Создание записи об изменении
     */
    private TarifficationChanges createMeshChangeRecord(NamingMesh mesh,
                                                        TarifficationChanges.ChangeType changeType,
                                                        String description) {
        TarifficationChanges change = new TarifficationChanges();
        change.setSubjectName(mesh.getSubjectName());
        change.setClassName(mesh.getClassName());
        change.setGroupNameEducationalPlan(mesh.getGroupNameEducationalPlan());
        change.setGroupNameMesh(mesh.getGroupNameMesh());
        change.setChangeType(changeType);
        change.setChangeDate(LocalDateTime.now());

        // Добавляем описание в поле FIO (временно)
        change.setChangeType(MESH_MAPPING_CHANGED);


        return change;
    }

    /**
     * Построение описания изменений
     */
    private String buildChangeDescription(NamingMesh oldMesh, NamingMesh newMesh) {
        StringBuilder description = new StringBuilder("Изменение связи: ");

        boolean hasChanges = false;

        if (!Objects.equals(oldMesh.getClassNameMesh(), newMesh.getClassNameMesh())) {
            description.append("Класс МЭШ '").append(oldMesh.getClassNameMesh())
                    .append("' → '").append(newMesh.getClassNameMesh()).append("'; ");
            hasChanges = true;
        }

        if (!Objects.equals(oldMesh.getGroupNameMesh(), newMesh.getGroupNameMesh())) {
            description.append("Группа МЭШ '").append(oldMesh.getGroupNameMesh())
                    .append("' → '").append(newMesh.getGroupNameMesh()).append("'");
            hasChanges = true;
        }

        if (!hasChanges) {
            description.append("Неизвестные изменения");
        }

        return description.toString();
    }

    /**
     * Вспомогательный метод для чтения значения ячейки
     */
    private String getCellValue(Row row, int columnIndex) {
        if (columnIndex < 0 || row == null) {
            return null;
        }

        Cell cell = row.getCell(columnIndex);
        return getCellValueAsString(cell);
    }

    /**
     * Вспомогательный метод для чтения значения ячейки
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Для числовых значений возвращаем целое число
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (int) numericValue) {
                        return String.valueOf((int) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            default:
                return null;
        }
    }

    /**
     * Получение всех уникальных предметов
     */
    public List<String> getAllUniqueSubjects() {
        List<NamingMesh> allMeshes = getAllNamingMeshes();
        Set<String> subjects = new HashSet<>();

        for (NamingMesh mesh : allMeshes) {
            if (mesh.getSubjectName() != null && !mesh.getSubjectName().trim().isEmpty()) {
                subjects.add(mesh.getSubjectName().trim());
            }
        }

        List<String> result = new ArrayList<>(subjects);
        Collections.sort(result);
        return result;
    }

    /**
     * Получение всех уникальных классов для предмета
     */
    public List<String> getClassesForSubject(String subjectName) {
        List<NamingMesh> allMeshes = getAllNamingMeshes();
        Set<String> classes = new HashSet<>();

        for (NamingMesh mesh : allMeshes) {
            if (subjectName.equals(mesh.getSubjectName()) &&
                    mesh.getClassName() != null && !mesh.getClassName().trim().isEmpty()) {
                classes.add(mesh.getClassName().trim());
            }
        }

        List<String> result = new ArrayList<>(classes);
        Collections.sort(result);
        return result;
    }
}