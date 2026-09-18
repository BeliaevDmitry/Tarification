package org.school.personalLoad.pa.repository;

import org.school.personalLoad.pa.model.PaWorkMaterialFile;
import org.school.personalLoad.pa.model.PaWorkMaterialFileKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaWorkMaterialFileRepository extends JpaRepository<PaWorkMaterialFile, Long> {
    List<PaWorkMaterialFile> findAllByMaterialIdOrderByKindAscOriginalFileNameAscIdAsc(Long materialId);
    List<PaWorkMaterialFile> findAllByMaterialIdInOrderByMaterialIdAscKindAscOriginalFileNameAscIdAsc(List<Long> materialIds);
    Optional<PaWorkMaterialFile> findFirstByMaterialIdAndKindAndOriginalFileNameIgnoreCaseOrderByIdDesc(
            Long materialId, PaWorkMaterialFileKind kind, String originalFileName);
}
