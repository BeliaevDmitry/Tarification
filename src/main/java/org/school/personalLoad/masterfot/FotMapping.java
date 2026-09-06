package org.school.personalLoad.masterfot;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "master_fot_mapping")
public class FotMapping {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private String academicYear;
    @Column(nullable = false, length = 32) private String type;
    @Column(nullable = false, length = 2000) private String source;
    @Column(nullable = false, length = 2000) private String target;
}
