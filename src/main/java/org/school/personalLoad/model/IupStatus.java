package org.school.personalLoad.model;

public enum IupStatus {
    DRAFT,
    REVIEW,
    APPROVED,
    ACTIVE,
    CHANGED,
    COMPLETED,
    CANCELLED;

    public boolean affectsHeadcount() {
        return this == APPROVED || this == ACTIVE;
    }
}
