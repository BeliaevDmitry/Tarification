package org.school.personalLoad.vsoko.mcko.repository;

import org.school.personalLoad.vsoko.mcko.model.VsokoMckoImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VsokoMckoImportBatchRepository extends JpaRepository<VsokoMckoImportBatch, Long> {
    List<VsokoMckoImportBatch> findTop50ByOrderByUploadedAtDesc();
}
