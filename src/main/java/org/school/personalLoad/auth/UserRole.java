package org.school.personalLoad.auth;

public enum UserRole {
    ADMIN("Администратор"),
    DIRECTOR("Директор"),
    DEPUTY_DIRECTOR("Заместитель директора"),
    BUILDING_HEAD("Руководитель корпуса"),
    METHODIST("Методист"),
    HR("Кадры");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
