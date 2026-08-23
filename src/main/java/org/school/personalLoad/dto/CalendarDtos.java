package org.school.personalLoad.dto;

import org.school.personalLoad.model.CalendarEventVisibility;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class CalendarDtos {
    private CalendarDtos() {
    }

    public record PersonRef(Long id, String fullName, String position, String buildingCode) {
    }

    public record BuildingRef(Long id, String code, String name, String address) {
    }

    public record EventView(Long id,
                            String source,
                            String title,
                            LocalDate date,
                            LocalTime startTime,
                            LocalTime endTime,
                            int durationMinutes,
                            String place,
                            CalendarEventVisibility visibility,
                            String visibilityLabel,
                            Long ownerUserId,
                            Long ownerTeacherId,
                            String ownerName,
                            String color,
                            String audienceSummary,
                            List<PersonRef> participants,
                            List<BuildingRef> buildings,
                            List<Long> selectedPersonIds,
                            List<String> selectedGroupCodes,
                            List<Long> selectedBuildingIds,
                            List<Long> selectedCustomListIds,
                            boolean canEdit) {
    }

    public record EventRequest(String title,
                               LocalDate date,
                               LocalTime startTime,
                               Integer durationMinutes,
                               String place,
                               CalendarEventVisibility visibility,
                               List<Long> personIds,
                               List<String> groupCodes,
                               List<Long> buildingIds,
                               List<Long> customListIds) {
    }

    public record VisibilityOption(String code, String label) {
    }

    public record PreferencesView(Long ownerUserId,
                                  Long ownerTeacherId,
                                  String color,
                                  CalendarEventVisibility defaultVisibility,
                                  List<Long> sharedWithPersonIds) {
    }

    public record PreferencesRequest(String color,
                                     CalendarEventVisibility defaultVisibility,
                                     List<Long> sharedWithPersonIds) {
    }

    public record CustomListView(Long id, String name, List<Long> personIds) {
    }

    public record CustomListRequest(String name, List<Long> personIds) {
    }

    public record BootstrapView(PreferencesView preferences,
                                List<VisibilityOption> visibilityOptions,
                                List<CustomListView> customLists) {
    }
}
