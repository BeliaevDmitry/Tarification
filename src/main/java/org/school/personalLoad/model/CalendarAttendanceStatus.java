package org.school.personalLoad.model;

public enum CalendarAttendanceStatus {
    PENDING("Ожидается ответ"),
    ACCEPTED("Придёт"),
    DECLINED("Не придёт");

    private final String displayName;

    CalendarAttendanceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
