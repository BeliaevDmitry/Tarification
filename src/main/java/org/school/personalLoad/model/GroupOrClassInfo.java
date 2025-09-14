package org.school.personalLoad.model;
//информация по педагогам и предметам из МЭШ

public class GroupOrClassInfo {
    private final String classNameMesh;
    private final int studentCountMesh;
    private final String teacherNameMesh;

    public GroupOrClassInfo(String classNameMesh, int studentCountMesh, String teacherNameMesh) {
        this.classNameMesh = classNameMesh;
        this.studentCountMesh = studentCountMesh;
        this.teacherNameMesh = teacherNameMesh;
    }

    public String getClassNameMesh() { return classNameMesh; }
    public int getStudentCountMesh() { return studentCountMesh; }
    public String getTeacherNameMesh() { return teacherNameMesh; }
}
