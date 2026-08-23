package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "probe_order_approval", uniqueConstraints = {
        @UniqueConstraint(name = "uk_probe_order_approval_scope",
                columnNames = {"order_id", "scope_type", "scope_code"})
}, indexes = {
        @Index(name = "idx_probe_order_approval_order", columnList = "order_id")
})
public class ProbeOrderApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProbeOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private ProbeOrderApprovalScope scopeType;

    @Column(name = "scope_code", nullable = false, length = 255)
    private String scopeCode;

    @Column(name = "scope_label", nullable = false, length = 500)
    private String scopeLabel;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", nullable = false, length = 255)
    private String approvedBy;
}
