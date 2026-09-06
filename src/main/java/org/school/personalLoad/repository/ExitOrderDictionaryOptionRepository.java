package org.school.personalLoad.repository;

import org.school.personalLoad.model.ExitOrderDictionaryOption;
import org.school.personalLoad.model.ExitOrderDictionaryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExitOrderDictionaryOptionRepository extends JpaRepository<ExitOrderDictionaryOption, Long> {
    List<ExitOrderDictionaryOption> findAllByOrderByTypeAscSortOrderAsc();
    List<ExitOrderDictionaryOption> findAllByTypeOrderBySortOrderAsc(ExitOrderDictionaryType type);
    void deleteAllByType(ExitOrderDictionaryType type);
}
