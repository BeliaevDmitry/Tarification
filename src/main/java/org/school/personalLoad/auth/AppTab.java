package org.school.personalLoad.auth;

import java.util.Arrays;
import java.util.List;

public enum AppTab {
    BUILDINGS("Корпуса", "/buildings.html"),
    CLASSES("Классы", "/classes.html"),
    SUBJECTS("Предметы", "/subjects.html"),
    CURRICULUM("Учебный план", "/curriculum.html"),
    LOAD("Нагрузка по корпусам", "/load.html"),
    TEACHERS("Педагоги", "/teachers.html"),
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
        return Arrays.asList(BUILDINGS, CLASSES, SUBJECTS, CURRICULUM, LOAD, TEACHERS, USERS);
    }
}
