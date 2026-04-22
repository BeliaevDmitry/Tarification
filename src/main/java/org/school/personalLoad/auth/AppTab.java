package org.school.personalLoad.auth;

import java.util.Arrays;
import java.util.List;

public enum AppTab {
    BUILDINGS("Корпуса", "/buildings.html"),
    CLASSES("Классы", "/classes.html"),
    SUBJECTS("Предметы", "/subjects.html"),
    CURRICULUM("Учебный план", "/curriculum.html"),
    LOAD("Нагрузка по корпусам", "/load.html"),
    LOAD_STATS("Нагрузка: статистика", "/load.html#stats"),
    SERVICE_NOTES("Служебные записки", "/service-notes.html"),
    SETTINGS("Настройки", "/settings.html"),
    TEACHERS("Педагоги", "/teachers.html"),
    CONTINGENT_IMPORT("Контингент: импорт", "/contingent.html#import"),
    CONTINGENT_STATS("Контингент: численность", "/contingent.html#stats"),
    SUBJECT_AREAS("Предметные области", "/subject-areas.html"),
    VSOKO_VIEW("ВСОКО: просмотр", "/vsoko.html"),
    VSOKO_EDIT("ВСОКО: редактирование", "/vsoko-oge.html"),
    OGE_UPLOAD_VIEW("ВСОКО: ОГЭ/Выгрузка (просмотр)", "/vsoko-oge.html#upload"),
    OGE_MISMATCH_VIEW("ВСОКО: ОГЭ/Нестыковки (просмотр)", "/vsoko-oge.html#mismatches"),
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
        return Arrays.asList(BUILDINGS, CLASSES, SUBJECTS, CURRICULUM, LOAD, LOAD_STATS, SERVICE_NOTES, SETTINGS, TEACHERS, CONTINGENT_IMPORT, CONTINGENT_STATS, SUBJECT_AREAS, VSOKO_VIEW, VSOKO_EDIT, OGE_UPLOAD_VIEW, OGE_MISMATCH_VIEW, OGE_GIA_UPLOAD, OGE_WORK_UPLOAD, USERS);
    }
}
