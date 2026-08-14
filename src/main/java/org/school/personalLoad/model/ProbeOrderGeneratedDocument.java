package org.school.personalLoad.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "probe_order_generated_document", uniqueConstraints = {
        @UniqueConstraint(name = "uk_probe_order_generated_document", columnNames = "order_id")
})
public class ProbeOrderGeneratedDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProbeOrder order;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] content;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "generated_by", nullable = false, length = 255)
    private String generatedBy;
}
