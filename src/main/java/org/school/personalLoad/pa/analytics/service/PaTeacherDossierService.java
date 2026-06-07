package org.school.personalLoad.pa.analytics.service;

import java.io.IOException;

public interface PaTeacherDossierService {

    byte[] generateTeacherDossier(String academicYear, String teacherFio) throws IOException;
}
