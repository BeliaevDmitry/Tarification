package org.school.personalLoad.service;

import org.school.personalLoad.model.NamingMesh;
import org.school.personalLoad.model.TarifficationChangesMesh;

import java.util.List;
import java.util.Optional;

public interface NamingMeshService {

    List<TarifficationChangesMesh> processNamingMeshFile(String filePath);
    List<NamingMesh> getAllNamingMeshes();
    Optional<NamingMesh> findNamingMesh(String subjectName, String className, String groupNameEducationalPlan);
    void clearAllNamingMeshes();
    void saveNamingMeshes(List<NamingMesh> namingMeshes);
    void updateNamingMeshRelations();
    boolean existsNamingMesh(String subjectName, String className, String groupNameEducationalPlan);
    String getClassNameMesh(String subjectName, String className, String groupNameEducationalPlan);
    String getGroupNameMesh(String subjectName, String className, String groupNameEducationalPlan);
    long getNamingMeshCount();
    boolean deleteNamingMesh(String subjectName, String className, String groupNameEducationalPlan);
    boolean hasChanges(List<NamingMesh> newNamingMeshes);
    List<String> getAllUniqueSubjects();
    List<String> getClassesForSubject(String subjectName);
}