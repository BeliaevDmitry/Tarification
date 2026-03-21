package org.school.personalLoad.service.impl;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.audit.ActionType;
import org.school.personalLoad.audit.AuditService;
import org.school.personalLoad.dto.SchoolBuildingRequest;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.security.CurrentUserService;
import org.school.personalLoad.service.SchoolBuildingService;
import org.school.personalLoad.user.AppUser;
import org.school.personalLoad.user.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolBuildingServiceImpl implements SchoolBuildingService {

    private final SchoolBuildingRepository repository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Override
    public SchoolBuilding upsert(SchoolBuildingRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        String code = normalize(request.getCode());
        String name = normalize(request.getName());
        if (code.isBlank()) throw new IllegalArgumentException("code is required");
        if (name.isBlank()) throw new IllegalArgumentException("name is required");

        AppUser user = currentUserService.requireCurrentUser();
        SchoolBuilding entity = repository.findByCode(code).orElseGet(SchoolBuilding::new);
        boolean creating = entity.getId() == null;
        if (user.getRole() == RoleName.BUILDING_HEAD) {
            if (creating) {
                throw new IllegalArgumentException("BUILDING_HEAD cannot create buildings");
            }
            if (entity.getHeadUserId() == null || !entity.getHeadUserId().equals(user.getId())) {
                throw new IllegalArgumentException("You can update only your building");
            }
        }
        SchoolBuilding oldValue = copy(entity);
        entity.setCode(code);
        entity.setName(name);
        SchoolBuilding saved = repository.save(entity);
        auditService.log(creating ? ActionType.CREATE : ActionType.UPDATE, "Building", saved.getId(), creating ? null : oldValue, saved, creating ? "Building created" : "Building updated");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SchoolBuilding> findAll() {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() == RoleName.BUILDING_HEAD) {
            return repository.findByHeadUserId(user.getId()).map(List::of).orElse(List.of());
        }
        return repository.findAll();
    }

    @Override
    public void clearAll() {
        List<SchoolBuilding> oldValue = repository.findAll();
        repository.deleteAll();
        auditService.log(ActionType.DELETE, "Building", null, oldValue, null, "All buildings removed");
    }

    private SchoolBuilding copy(SchoolBuilding source) {
        SchoolBuilding target = new SchoolBuilding();
        target.setId(source.getId());
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setHeadUserId(source.getHeadUserId());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
