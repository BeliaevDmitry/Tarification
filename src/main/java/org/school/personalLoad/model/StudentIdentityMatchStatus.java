package org.school.personalLoad.model;

public enum StudentIdentityMatchStatus {
    PENDING,
    LINKED_BY_RECORD_NUMBER,
    LINKED_BY_NAME_AND_BIRTH_DATE,
    LINKED_BY_NAME_ONLY,
    CREATED,
    AMBIGUOUS,
    MANUALLY_LINKED
}
