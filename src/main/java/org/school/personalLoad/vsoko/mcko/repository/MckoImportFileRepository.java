package org.school.personalLoad.vsoko.mcko.repository;

import org.school.personalLoad.vsoko.mcko.model.MckoImportFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MckoImportFileRepository extends JpaRepository<MckoImportFile, Long> {
    List<MckoImportFile> findAllByBatchIdOrderByIdAsc(Long batchId);
    List<MckoImportFile> findTop200ByOrderByIdDesc();
    List<MckoImportFile> findAllByOrderByIdDesc(Pageable pageable);
}
