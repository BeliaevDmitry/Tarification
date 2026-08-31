package org.school.personalLoad.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "probe_order_settings")
public class ProbeOrderSettings {
    public static final Long DEFAULT_ID = 1L;

    @Id
    private Long id = DEFAULT_ID;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", nullable = false, length = 32)
    private ProbeOrderApprovalMode approvalMode = ProbeOrderApprovalMode.ORGANIZATIONAL_BUILDING;

    @Column(name = "deputy_director_teacher_id")
    private Long deputyDirectorTeacherId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy = "SYSTEM";
}
