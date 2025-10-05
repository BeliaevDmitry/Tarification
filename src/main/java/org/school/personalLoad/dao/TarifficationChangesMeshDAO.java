package org.school.personalLoad.dao;

import org.school.personalLoad.model.TarifficationChangesMesh;
import java.util.List;

public interface TarifficationChangesMeshDAO {

    void save(TarifficationChangesMesh change);

    void saveAll(List<TarifficationChangesMesh> changes);

    List<TarifficationChangesMesh> findAll();

    List<TarifficationChangesMesh> findByTarifficationChangeId(Long changeId);

    List<TarifficationChangesMesh> findByFioTeacher(String fioTeacher);

    List<TarifficationChangesMesh> findByGroupNameMesh(String groupNameMesh);

    List<TarifficationChangesMesh> findByMeshChangeType(TarifficationChangesMesh.MeshChangeType changeType);

    void deleteAll();
}