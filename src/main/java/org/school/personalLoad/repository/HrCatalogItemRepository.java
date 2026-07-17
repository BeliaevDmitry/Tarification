package org.school.personalLoad.repository;
import org.school.personalLoad.model.HrCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HrCatalogItemRepository extends JpaRepository<HrCatalogItem, Long> {
    List<HrCatalogItem> findAllBySchoolCodeAndActiveTrueOrderByName(String schoolCode);
}
