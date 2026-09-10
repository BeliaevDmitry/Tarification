package org.school.personalLoad.model;

public enum AdmissionDocumentStatus {
    MOS_RU_SUBMITTED,
    PRELIMINARY_INVITATION,
    PLACE_OFFERED,
    SIGNED,
    INVITATION,
    ENROLLED,
    DOCUMENTS_WITHDRAWN,
    // Legacy values are retained so previously saved applications remain readable.
    INTERVIEW_INVITED,
    ENROLLMENT
}
