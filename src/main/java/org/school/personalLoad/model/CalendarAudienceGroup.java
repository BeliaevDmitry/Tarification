package org.school.personalLoad.model;

public enum CalendarAudienceGroup {
    DEPUTIES("Замы"),
    ADMINISTRATION("Администрация"),
    FULL_ADMINISTRATION("Полная администрация");

    private final String displayName;

    CalendarAudienceGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
