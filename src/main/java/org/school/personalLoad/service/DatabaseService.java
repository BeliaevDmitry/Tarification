package org.school.personalLoad.service;

import org.school.personalLoad.model.TarifficationChanges;
import org.school.personalLoad.model.TarifficationPerson;
import org.school.personalLoad.model.NamingMesh;

import java.util.List;
import java.util.Optional;

public interface DatabaseService {

    void compareAndSave(List<TarifficationPerson> newTariffication);
    void compareAndSave(List<TarifficationPerson> newTariffication, List<NamingMesh> namingMeshes);
    List<TarifficationChanges> compareWithHistory(List<TarifficationPerson> newTariffication);
    List<TarifficationChanges> getAllHistory();
    void saveCurrentTariffication(List<TarifficationPerson> tarifficationList);
    void fullReset();
    List<TarifficationPerson> findAllByFieldsHistory(String subject, String className, String NumberSchoolBuilding);
    List<String> findAllUniqueClassAndGroupNames();

    // Новые методы для NamingMesh
    List<NamingMesh> getAllNamingMeshes();
    void saveNamingMeshes(List<NamingMesh> namingMeshes);
    Optional<NamingMesh> findNamingMesh(String subjectName, String className, String groupNameEducationalPlan);
    List<TarifficationPerson> findAllPersonsWithMesh();
    List<TarifficationPerson> findPersonsByTeacherWithMesh(String fioTeacher);
    void updateNamingMeshRelations();
}