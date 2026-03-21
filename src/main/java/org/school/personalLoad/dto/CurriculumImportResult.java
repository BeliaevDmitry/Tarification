package org.school.personalLoad.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumImportResult {
    private int created;
    private int updated;
    private int deprecated;
    private int classesCreated;
    private int orphanedLoads;
    private int subjectsImported;
}
