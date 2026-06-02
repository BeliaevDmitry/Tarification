package org.school.personalLoad.model;

public enum SubjectType {
    CORE,
    FORMABLE,
    EXTRACURRICULAR,
    CORRECTIONAL,
    /**
     * Legacy value for backward compatibility with historical rows.
     * New records should use CORE or FORMABLE explicitly.
     */
    CORE_FORMABLE
}
