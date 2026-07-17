package org.school.personalLoad.model;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hr_document_version")
public class HrDocumentVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String documentType;
    @Column(nullable = false) private Long documentId;
    @Column(nullable = false) private int revision;
    @Column(nullable = false) private String filename;
    @Lob @Column(nullable = false) private byte[] content;
    @Column(nullable = false) private String source;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private String createdBy;
}
