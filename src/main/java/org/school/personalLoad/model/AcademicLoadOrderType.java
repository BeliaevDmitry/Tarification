package org.school.personalLoad.model;

public enum AcademicLoadOrderType {
    CURRICULUM_APPROVAL("Об утверждении учебных планов"),
    LOAD_APPROVAL("Об утверждении учебной нагрузки");

    private final String displayName;

    AcademicLoadOrderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
