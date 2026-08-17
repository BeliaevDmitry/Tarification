package org.school.ordergen.service;

import org.school.ordergen.config.AppConfig;
import org.school.ordergen.model.*;
import org.school.ordergen.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGenerationService {

    private final ExcelReaderService excelReader;
    private final TemplateFillerService templateFiller;
    private final ProcessedEventRepository processedRepo;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_RANGE_PARSER = DateTimeFormatter.ofPattern("H:mm");

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Запуск генерации приказов при старте приложения");
        generateOrders();
    }

    public void generateOrders() {
        log.info("Запуск генерации приказов");

        List<Student> students = excelReader.loadStudents();
        Map<String, ClassTeacher> classTeacherMap = excelReader.loadClassTeachers();
        Map<String, SchoolBuilding> buildingMap = excelReader.loadBuildings();
        List<Event> events = excelReader.loadEvents();
        List<Application> applications = excelReader.loadApplications();

        LocalDate today = LocalDate.now();
        int generatedCount = 0;

        for (Event event : events) {
            LocalDate eventDate;
            try {
                eventDate = LocalDate.parse(event.getDate(), DATE_FORMAT);
            } catch (DateTimeParseException e) {
                log.warn("Неверный формат даты у события {}: {}", event.getId(), event.getDate());
                continue;
            }
            if (eventDate.isBefore(today)) continue;

            List<Application> eventApps = applications.stream()
                    .filter(a -> a.getEventId().equals(event.getId()))
                    .collect(Collectors.toList());
            if (eventApps.isEmpty()) continue;

            Map<String, List<Application>> byBuilding = groupByBuilding(eventApps, students, classTeacherMap);

            for (Map.Entry<String, List<Application>> entry : byBuilding.entrySet()) {
                String buildingAddress = entry.getKey();
                List<Application> apps = entry.getValue();

                if (processedRepo.existsByEventIdAndBuildingAddress(event.getId(), buildingAddress)) {
                    log.debug("Событие {} для корпуса {} уже обработано", event.getId(), buildingAddress);
                    continue;
                }

                OrderData data = buildOrderData(event, apps, students, classTeacherMap, buildingMap, buildingAddress);
                if (data == null) {
                    log.warn("Не удалось сформировать данные для события {} корпус {}", event.getId(), buildingAddress);
                    continue;
                }

                String fileName = generateFileName(event, buildingAddress);

                try {
                    templateFiller.fillTemplate(data, fileName);
                    processedRepo.save(new ProcessedEvent(event.getId(), buildingAddress, fileName));
                    generatedCount++;
                    log.info("Сгенерирован приказ для события {} корпус {}", event.getId(), buildingAddress);
                } catch (Exception e) {
                    log.error("Ошибка при генерации приказа для события " + event.getId(), e);
                }
            }
        }
        log.info("Генерация завершена. Создано приказов: {}", generatedCount);
    }

    private Map<String, List<Application>> groupByBuilding(List<Application> apps,
                                                           List<Student> students,
                                                           Map<String, ClassTeacher> classTeacherMap) {
        Map<String, List<Application>> result = new HashMap<>();
        for (Application app : apps) {
            Student student = findStudent(students, app.getStudentName());
            if (student == null) {
                log.warn("Ученик не найден: {}", app.getStudentName());
                continue;
            }
            String className = normalizeClassName(student.getClassName());
            ClassTeacher teacher = classTeacherMap.get(className);
            if (teacher == null) {
                log.warn("Классный руководитель не найден для класса {}", className);
                continue;
            }
            String building = teacher.getBuildingAddress();
            result.computeIfAbsent(building, k -> new ArrayList<>()).add(app);
        }
        return result;
    }

    private Student findStudent(List<Student> students, String fullName) {
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        for (Student s : students) {
            if (s.getFullName().trim().replaceAll("\\s+", " ").equalsIgnoreCase(normalized)) {
                return s;
            }
        }
        return null;
    }

    private String normalizeClassName(String raw) {
        if (raw == null) return "";
        // Удаляем всё, кроме цифр и букв (русских и латинских)
        return raw.replaceAll("[^0-9а-яА-Яa-zA-Z]", "");
    }

    private OrderData buildOrderData(Event event,
                                     List<Application> apps,
                                     List<Student> students,
                                     Map<String, ClassTeacher> classTeacherMap,
                                     Map<String, SchoolBuilding> buildingMap,
                                     String buildingAddress) {
        Set<String> uniqueClassNames = new TreeSet<>();
        Map<String, ClassTeacher> teachersUsed = new HashMap<>();
        for (Application app : apps) {
            Student student = findStudent(students, app.getStudentName());
            if (student == null) continue;
            String className = normalizeClassName(student.getClassName());
            uniqueClassNames.add(className);
            teachersUsed.putIfAbsent(className, classTeacherMap.get(className));
        }

        List<ClassTeacher> teacherList = new ArrayList<>(teachersUsed.values());
        teacherList.sort(Comparator.comparing(ClassTeacher::getClassName));
        String classWord = uniqueClassNames.size() == 1 ? "класса" : "классов";

        // Базовые значения (могут быть переопределены ниже)
        String firstTeacherDative = teacherList.isEmpty() ? "" : teacherList.get(0).getDative();
        String firstTeacherFullName = teacherList.isEmpty() ? "" : teacherList.get(0).getFullName();

        // --- Новая логика назначения руководителя и заместителей ---
        String leaderPart;
        String deputyPart;
        String accompanyingTitle;
        List<ClassTeacher> accompanyingList = new ArrayList<>();

        // Уникализация списка классных руководителей (по accusative, сохраняем порядок)
        Set<String> seen = new LinkedHashSet<>();
        List<ClassTeacher> uniqueTeachers = teacherList.stream()
                .filter(t -> seen.add(t.getAccusative()))
                .collect(Collectors.toList());

        int childrenCount = apps.size();
        int need = (int) Math.ceil(childrenCount / 10.0);
        if (need < 1) need = 1; // подстраховка

        if (childrenCount <= 10) {
            // Детей ≤ 10: выводим всех уникальных учителей (для ручной корректировки)
            if (uniqueTeachers.isEmpty()) {
                // экстренный случай – заполняем заглушками
                leaderPart = "__________________________";
                deputyPart = " и возложить на него";
                accompanyingTitle = "Сопровождающий";
                ClassTeacher dummy = createDummyTeacher();
                accompanyingList.add(dummy);
                firstTeacherDative = dummy.getDative();
            } else {
                leaderPart = uniqueTeachers.get(0).getAccusative();
                if (uniqueTeachers.size() > 1) {
                    deputyPart = " и возложить на них";
                } else {
                    deputyPart = " и возложить на него";
                }
                accompanyingTitle = (uniqueTeachers.size() == 1) ? "Сопровождающий" : "Сопровождающие";
                accompanyingList.addAll(uniqueTeachers);
                // Для возможности ручной вставки формируем строку дательных падежей всех учителей
                firstTeacherDative = uniqueTeachers.stream()
                        .map(ClassTeacher::getDative)
                        .collect(Collectors.joining(", "));
            }
        } else {
            // Детей > 10: формируем ровно need сопровождающих
            List<ClassTeacher> selected = new ArrayList<>();
            for (int i = 0; i < need; i++) {
                if (i < uniqueTeachers.size()) {
                    selected.add(uniqueTeachers.get(i));
                } else {
                    selected.add(createDummyTeacher());
                }
            }

            leaderPart = selected.get(0).getAccusative();
            if (need > 1) {
                String deputies = selected.stream().skip(1)
                        .map(ClassTeacher::getAccusative)
                        .collect(Collectors.joining(", "));
                deputyPart = ", заместителем руководителя " + deputies + " и возложить на них";
            } else {
                deputyPart = " и возложить на него";
            }
            accompanyingTitle = (need == 1) ? "Сопровождающий" : "Сопровождающие";
            accompanyingList.addAll(selected);
            // firstTeacherDative оставляем как дательный первого учителя (из уникальных, если есть)
            // или от заглушки, если uniqueTeachers пуст
            if (!uniqueTeachers.isEmpty()) {
                firstTeacherDative = uniqueTeachers.get(0).getDative();
            } else {
                firstTeacherDative = createDummyTeacher().getDative();
            }
        }

        // Формируем строку сопровождающих (с номерами телефонов)
        StringBuilder accompanyingBuilder = new StringBuilder();
        for (ClassTeacher t : accompanyingList) {
            if (!accompanyingBuilder.isEmpty()) {
                accompanyingBuilder.append("\n");
            }
            String name = t.getFullName();
            if (t.getTeacherPhone() != null && !t.getTeacherPhone().isEmpty()) {
                name += " " + t.getTeacherPhone();
            }
            accompanyingBuilder.append(name);
        }
        String accompanying = accompanyingBuilder.toString();

        // --- Остальная часть метода без изменений ---
        String[] times = event.getTimeRange().split("-");
        if (times.length < 2) {
            log.warn("Неверный формат времени: {}", event.getTimeRange());
            return null;
        }
        LocalTime start, end;
        try {
            start = LocalTime.parse(times[0].trim(), TIME_RANGE_PARSER);
            end = LocalTime.parse(times[1].trim(), TIME_RANGE_PARSER);
        } catch (Exception e) {
            log.warn("Ошибка парсинга времени: {}", event.getTimeRange());
            return null;
        }
        LocalTime gathering = start.minusHours(1);
        LocalTime returnTime = end.plusHours(1).plusMinutes(30);

        List<StudentInfo> studentInfos = new ArrayList<>();
        for (Application app : apps) {
            Student student = findStudent(students, app.getStudentName());
            if (student == null) continue;
            studentInfos.add(StudentInfo.builder()
                    .fullName(student.getFullName())
                    .parentName(student.getParentName())
                    .parentPhone(student.getPhone())
                    .build());
        }

        SchoolBuilding building = buildingMap.get(buildingAddress);
        String curator = (building != null) ? building.getCuratorName() : "";

        return OrderData.builder()
                .eventDate(event.getDate())
                .className(formatClassNames(uniqueClassNames))
                .number(String.valueOf(apps.size()))
                .venue(event.getOrganizer())
                .address(event.getAddress())
                .eventTime(times[0].trim())
                .leader(leaderPart)
                .deputy(deputyPart)
                .gatheringTime(gathering.format(TIME_FORMAT))
                .gatheringPlace(buildingAddress)
                .returnTime(returnTime.format(TIME_FORMAT))
                .curator(curator)
                .students(studentInfos)
                .accompanying(accompanying)
                .leaderName(firstTeacherFullName)
                .leaderDative(firstTeacherDative)
                .classWord(classWord)
                .accompanyingTitle(accompanyingTitle)
                .build();
    }

    // Вспомогательный метод для создания заглушки сопровождающего
    private ClassTeacher createDummyTeacher() {
        ClassTeacher dummy = ClassTeacher.builder()
                .fullName("__________________________")
                .teacherPhone("")
                .build();
        dummy.setAccusative("__________________________");
        dummy.setDative("__________________________");
        return dummy;
    }

    private String formatClassNames(Set<String> classNames) {
        return classNames.stream()
                .map(cn -> {
                    String number = cn.replaceAll("[^0-9]", "");
                    String letters = cn.replaceAll("[0-9]", "");
                    return number + " «" + letters + "»";
                })
                .collect(Collectors.joining(", "));
    }

    private String generateFileName(Event event, String buildingAddress) {
        // Очищаем адрес: оставляем только буквы и цифры
        String shortBuilding = buildingAddress.replaceAll("[^a-zA-Zа-яА-Я0-9]", "");
        if (shortBuilding.length() > 20) shortBuilding = shortBuilding.substring(0, 20);

        // Очищаем организатора: заменяем недопустимые символы на подчёркивание
        String organizer = event.getOrganizer();
        String safeOrganizer = organizer != null ? organizer : "";
        safeOrganizer = safeOrganizer.replaceAll("[\\\\/:*?\"<>|]", "_")  // запрещённые символы
                .replace(' ', '_')                  // пробелы на подчёркивание
                .replaceAll("_+", "_");             // убираем повторяющиеся подчёркивания
        if (safeOrganizer.length() > 30) safeOrganizer = safeOrganizer.substring(0, 30);

        return String.format("Приказ_на_%s_%s_%s_%s.docx",
                event.getDate().replace(".", ""),
                shortBuilding,
                safeOrganizer,
                event.getId());
    }
}