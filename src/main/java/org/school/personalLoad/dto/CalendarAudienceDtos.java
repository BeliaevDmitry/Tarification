package org.school.personalLoad.dto;

import java.util.List;

public final class CalendarAudienceDtos {
    private CalendarAudienceDtos() {
    }

    public record PersonOption(Long id,
                               String fullName,
                               String position,
                               String buildingCode) {
    }

    public record BuildingOption(Long id,
                                 String code,
                                 String name,
                                 String address) {
    }

    public record GroupOption(String code,
                              String label,
                              List<Long> personIds) {
    }

    public record SettingsView(List<PersonOption> people,
                               List<BuildingOption> buildings,
                               List<GroupOption> groups,
                               boolean canEdit) {
    }

    public record GroupSelection(String code, List<Long> personIds) {
    }

    public record UpdateRequest(List<GroupSelection> groups) {
    }
}
