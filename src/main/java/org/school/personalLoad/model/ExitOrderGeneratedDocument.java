package org.school.personalLoad.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "exit_order_generated_document", uniqueConstraints =
        @UniqueConstraint(name = "uk_exit_order_generated_document_order", columnNames = "order_id"))
public class ExitOrderGeneratedDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ExitOrder order;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "generated_by", nullable = false, length = 255)
    private String generatedBy;
}
