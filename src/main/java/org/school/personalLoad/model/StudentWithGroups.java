package org.school.personalLoad.model;

import java.util.ArrayList;
import java.util.List;

public class StudentWithGroups {
    private String fullName;
    private List<String> groups;

    public StudentWithGroups(String fullName) {
        this.fullName = fullName;
        this.groups = new ArrayList<>();
    }

    // Геттеры и сеттеры
    public String getFullName() { return fullName; }
    public List<String> getGroups() { return groups; }
    public void addGroup(String group) { groups.add(group); }
}