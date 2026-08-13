package org.school.personalLoad.repository;

import org.school.personalLoad.model.CorrectionSpecialistCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectionSpecialistCatalogEntryRepository
        extends JpaRepository<CorrectionSpecialistCatalogEntry, Long> {
    Optional<CorrectionSpecialistCatalogEntry> findByNameIgnoreCase(String name);

    List<CorrectionSpecialistCatalogEntry> findAllByOrderByNameAsc();
}
