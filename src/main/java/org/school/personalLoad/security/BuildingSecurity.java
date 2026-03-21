package org.school.personalLoad.security;

import lombok.RequiredArgsConstructor;
import org.school.personalLoad.model.SchoolBuilding;
import org.school.personalLoad.repository.CurriculumPlanEntryRepository;
import org.school.personalLoad.repository.ManualLoadEntryRepository;
import org.school.personalLoad.repository.SchoolBuildingRepository;
import org.school.personalLoad.user.AppUser;
import org.school.personalLoad.user.RoleName;
import org.springframework.stereotype.Component;

@Component("buildingSecurity")
@RequiredArgsConstructor
public class BuildingSecurity {

    private final CurrentUserService currentUserService;
    private final SchoolBuildingRepository schoolBuildingRepository;
    private final ManualLoadEntryRepository manualLoadEntryRepository;
    private final CurriculumPlanEntryRepository curriculumPlanEntryRepository;

    public boolean isHeadOfBuilding(String buildingCode) {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() != RoleName.BUILDING_HEAD) {
            return false;
        }
        return schoolBuildingRepository.findByCode(buildingCode)
                .map(SchoolBuilding::getHeadUserId)
                .filter(user.getId()::equals)
                .isPresent();
    }

    public boolean canManageBuilding(String buildingCode) {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() == RoleName.ADMIN || user.getRole() == RoleName.DIRECTOR || user.getRole() == RoleName.DEPUTY_DIRECTOR) {
            return true;
        }
        return user.getRole() == RoleName.BUILDING_HEAD && isHeadOfBuilding(buildingCode);
    }

    public boolean canAccessManualLoad(Long entryId) {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() != RoleName.BUILDING_HEAD) {
            return true;
        }
        return manualLoadEntryRepository.findById(entryId)
                .map(entry -> isHeadOfBuilding(entry.getNumberSchoolBuilding()))
                .orElse(false);
    }

    public boolean canAccessCurriculum(Long entryId) {
        AppUser user = currentUserService.requireCurrentUser();
        if (user.getRole() != RoleName.BUILDING_HEAD) {
            return true;
        }
        return curriculumPlanEntryRepository.findById(entryId)
                .map(entry -> isHeadOfBuilding(entry.getNumberSchoolBuilding()))
                .orElse(false);
    }
}
