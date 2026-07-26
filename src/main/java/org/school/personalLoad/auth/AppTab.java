package org.school.personalLoad.auth;

import java.util.Arrays;
import java.util.List;

public enum AppTab {
    HR_DOCUMENTS("Кадры: документы", "/teachers-notification.html"),
    HR_PERSONAL_DATA("Кадры: персональные данные", "/teachers-notification.html"),
    BUILDINGS("Корпуса", "/buildings.html"),
    CLASSES("Классы", "/classes.html"),
    SUBJECTS("Предметы", "/subjects.html"),
    CURRICULUM("Учебный план", "/curriculum.html"),
    LOAD("Нагрузка по корпусам", "/load.html"),
    PEOPLE_LOAD("Нагрузка по людям", "/people-load.html"),
    LOAD_ISSUES("Возможные ошибки", "/load-issues.html"),
    LOAD_STATS("Статистика нагрузки", "/load-statistics.html"),
    LOAD_SALARY("Нагрузка: расчёт денег", "/people-load.html"),
    SERVICE_NOTES("СЛ. записки на изменение нагрузки", "/service-notes.html"),
    HR_NOTIFICATIONS_VIEW("Кадры: предварительная нагрузка (просмотр)", "/teachers-notification.html"),
    HR_NOTIFICATIONS_EDIT("Кадры: предварительная нагрузка", "/teachers-notification.html"),
    SETTINGS("Настройки", "/settings.html"),
    TEACHERS("Персонал", "/teachers.html"),
    TEACHERS_ARCHIVE("Архив", "/teachers.html#archive"),
    TEACHERS_DISMISSALS("Увольнения", "/teachers.html#dismissals"),
    TEACHERS_SETTINGS("Настройки кадров", "/teachers.html#settings"),
    TEACHERS_MCKO("МЦКО", "/teachers.html#mcko"),
    CONTINGENT_IMPORT("Контингент: импорт", "/contingent.html#import"),
    CONTINGENT_STATS("Контингент: численность", "/contingent.html#stats"),
    SUBJECT_AREAS("Предметные области", "/subject-areas.html"),
    EDUCATIONAL_WORK("Воспитательная работа", "/educational-work.html"),
    DOCUMENTS_PEDAGOGICAL_COUNCILS("Документы: педагогические советы", "/pedagogical-councils.html"),
    VSOKO_VIEW("ВСОКО: просмотр", "/vsoko.html"),
    VSOKO_EDIT("ВСОКО: редактирование", "/vsoko-oge.html"),
    OGE_UPLOAD_VIEW("ВСОКО: ОГЭ/Выгрузка (просмотр)", "/vsoko-oge.html#upload"),
    OGE_MISMATCH_VIEW("ВСОКО: ОГЭ/Нестыковки (просмотр)", "/vsoko-oge.html#mismatches"),
    OGE_EXTERNAL_WORKS_VIEW("ВСОКО: ОГЭ/Внешние работы (просмотр)", "/vsoko-oge.html#external-works"),
    OGE_TEACHER_BINDING_VIEW("ВСОКО: ОГЭ/Привязка к педагогу (просмотр)", "/vsoko-oge.html#teacher-binding"),
    OGE_SCORE_VIEW("ВСОКО: ОГЭ/Баллы за задания (просмотр)", "/vsoko-oge.html#scores"),
    OGE_EVALUATION_VIEW("ВСОКО: ОГЭ/Оценивание (просмотр)", "/vsoko-oge.html#evaluation"),
    OGE_GIA_UPLOAD("ВСОКО: загрузка выгрузок ГИА", "/vsoko-oge.html#upload"),
    OGE_WORK_UPLOAD("ВСОКО: загрузка работ ОГЭ", "/vsoko-oge.html#works"),
    USERS("Пользователи", "/admin.html");

    private final String displayName;
    private final String path;

    AppTab(String displayName, String path) {
        this.displayName = displayName;
        this.path = path;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPath() {
        return path;
    }

    public static List<AppTab> navigableTabs() {
        return Arrays.asList(HR_DOCUMENTS, HR_PERSONAL_DATA, BUILDINGS, CLASSES, SUBJECTS, CURRICULUM, LOAD, PEOPLE_LOAD, LOAD_ISSUES, LOAD_STATS,
                LOAD_SALARY, SETTINGS, SUBJECT_AREAS, TEACHERS, TEACHERS_ARCHIVE, TEACHERS_DISMISSALS,
                TEACHERS_SETTINGS, TEACHERS_MCKO, SERVICE_NOTES, HR_NOTIFICATIONS_VIEW, CONTINGENT_IMPORT, CONTINGENT_STATS,
                EDUCATIONAL_WORK, DOCUMENTS_PEDAGOGICAL_COUNCILS, VSOKO_VIEW, VSOKO_EDIT, OGE_UPLOAD_VIEW, OGE_MISMATCH_VIEW,
                OGE_EXTERNAL_WORKS_VIEW, OGE_TEACHER_BINDING_VIEW, OGE_SCORE_VIEW, OGE_EVALUATION_VIEW,
                OGE_GIA_UPLOAD, OGE_WORK_UPLOAD, USERS);
    }
}
