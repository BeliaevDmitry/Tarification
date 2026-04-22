package org.school.personalLoad.oge.service;

import org.school.personalLoad.oge.dto.OgeDtos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OgeDefaultScale {
    private OgeDefaultScale() {
    }

    public static List<OgeDtos.ScoreScaleRow> defaultRows() {
        List<OgeDtos.ScoreScaleRow> rows = new ArrayList<>();
        for (int score = 0; score <= 68; score++) {
            Map<String, Integer> grades = new LinkedHashMap<>();
            for (String subject : OgeSubjects.CORE_SUBJECTS) {
                grades.put(subject, defaultGrade(subject, score));
            }
            rows.add(new OgeDtos.ScoreScaleRow(score, grades));
        }
        return rows;
    }

    private static Integer defaultGrade(String subject, int score) {
        if (score < 13) return 2;
        if (score < 22) return 3;
        if (score < 31) return 4;
        return 5;
    }
}
