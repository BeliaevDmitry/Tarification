package org.school.personalLoad.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "naming_mesh")
@IdClass(NamingMesh.NamingMeshId.class) // Составной ключ
public class NamingMesh {

    @Id
    private String subjectName;

    @Id
    private String className;

    @Id
    private String groupNameEducationalPlan;

    private String groupNameMesh;
    private String classNameMesh;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamingMeshId implements Serializable {
        private String subjectName;
        private String className;
        private String groupNameEducationalPlan;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NamingMeshId that = (NamingMeshId) o;
            return Objects.equals(subjectName, that.subjectName) &&
                    Objects.equals(className, that.className) &&
                    Objects.equals(groupNameEducationalPlan, that.groupNameEducationalPlan);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjectName, className, groupNameEducationalPlan);
        }
    }
}