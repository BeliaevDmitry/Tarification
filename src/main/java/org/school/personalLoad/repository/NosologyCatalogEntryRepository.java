package org.school.personalLoad.repository;

import org.school.personalLoad.model.NosologyCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NosologyCatalogEntryRepository extends JpaRepository<NosologyCatalogEntry, Long> {
    Optional<NosologyCatalogEntry> findByCodeIgnoreCase(String code);

    List<NosologyCatalogEntry> findAllByOrderByCodeAsc();
}
