package org.school.personalLoad.model;

public enum CalendarEventVisibility {
    PRIVATE("Только мне"),
    PARTICIPANTS("Мне и участникам"),
    DEPUTIES("Участникам и заместителям"),
    ADMINISTRATION("Участникам и администрации"),
    EVERYONE("Всем сотрудникам");

    private final String displayName;

    CalendarEventVisibility(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
