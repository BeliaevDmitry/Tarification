package org.school.personalLoad.oge.repository;

import org.school.personalLoad.oge.model.SubjectAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectAliasRepository extends JpaRepository<SubjectAlias, Long> {
    List<SubjectAlias> findAllByScopeAndActiveTrue(String scope);

    Optional<SubjectAlias> findByScopeAndSourceNameIgnoreCase(String scope, String sourceName);
}
