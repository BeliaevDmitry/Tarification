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
            Map<String, Integer> g = new LinkedHashMap<>();
            g.put("Русский язык", grade(score, 12, 21, 30, 37));
            g.put("Математика", grade(score, 5, 21, 30, 31));
            g.put("Физика", grade(score, 9, 19, 29, 39));
            g.put("Химия", grade(score, 9, 20, 30, 38));
            g.put("Информатика и ИКТ", grade(score, 4, 10, 16, 21));
            g.put("Биология", grade(score, 12, 25, 37, 47));
            g.put("История", grade(score, 10, 20, 29, 37));
            g.put("География", grade(score, 11, 18, 25, 31));
            g.put("Английский язык", gradeLang(score));
            g.put("Немецкий язык", gradeLang(score));
            g.put("Французский язык", gradeLang(score));
            g.put("Обществознание", grade(score, 14, 23, 30, 37));
            g.put("Испанский язык", gradeLang(score));
            g.put("Литература", grade(score, 15, 24, 32, 40));
            rows.add(new OgeDtos.ScoreScaleRow(score, g));
        }
        return rows;
    }

    private static Integer grade(int score, int max2, int max3, int max4, int max5) {
        if (score < 0 || score > max5) return null;
        if (score <= max2) return 2;
        if (score <= max3) return 3;
        if (score <= max4) return 4;
        return 5;
    }

    private static Integer gradeLang(int score) {
        if (score < 0 || score > 68) return null;
        if (score <= 28) return 2;
        if (score <= 45) return 3;
        if (score <= 57) return 4;
        return 5;
    }
}
