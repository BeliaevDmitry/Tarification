package org.school.personalLoad.model;

public enum StudyPeriodSettingKey {
    YEAR_1_9(StudyPeriod.YEAR, 1, 9, "1–9 классы · учебный год"),
    H1_1_9(StudyPeriod.H1, 1, 9, "1–9 классы · 1 полугодие"),
    H2_1_9(StudyPeriod.H2, 1, 9, "1–9 классы · 2 полугодие"),
    H1_10(StudyPeriod.H1, 10, 10, "10 класс · 1 полугодие"),
    H2_10(StudyPeriod.H2, 10, 10, "10 класс · 2 полугодие"),
    H1_11(StudyPeriod.H1, 11, 11, "11 класс · 1 полугодие"),
    H2_11(StudyPeriod.H2, 11, 11, "11 класс · 2 полугодие");

    private final StudyPeriod studyPeriod;
    private final int parallelFrom;
    private final int parallelTo;
    private final String displayName;

    StudyPeriodSettingKey(StudyPeriod studyPeriod, int parallelFrom, int parallelTo, String displayName) {
        this.studyPeriod = studyPeriod;
        this.parallelFrom = parallelFrom;
        this.parallelTo = parallelTo;
        this.displayName = displayName;
    }

    public StudyPeriod getStudyPeriod() {
        return studyPeriod;
    }

    public int getParallelFrom() {
        return parallelFrom;
    }

    public int getParallelTo() {
        return parallelTo;
    }

    public String getDisplayName() {
        return displayName;
    }
}
