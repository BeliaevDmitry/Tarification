package org.school.personalLoad.model;
//информация по педагогам и предметам из МЭШ

public class GroupOrClassInfo {
    private final String className;
    private final int studentCount;
    private final String teacherName;

    public GroupOrClassInfo(String className, int studentCount, String teacherName) {
        this.className = className;
        this.studentCount = studentCount;
        this.teacherName = teacherName;
    }

    public String getClassName() { return className; }
    public int getStudentCount() { return studentCount; }
    public String getTeacherName() { return teacherName; }
}
