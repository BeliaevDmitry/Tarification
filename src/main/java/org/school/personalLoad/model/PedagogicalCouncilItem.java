package org.school.personalLoad.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "pedagogical_council_item", indexes = {
        @Index(name = "idx_ped_council_item_protocol", columnList = "protocol_id, item_order")
})
public class PedagogicalCouncilItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "protocol_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PedagogicalCouncilProtocol protocol;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(name = "agenda_title", nullable = false, length = 2000)
    private String agendaTitle;

    @Column(name = "agenda_duration_minutes")
    private Integer agendaDurationMinutes;

    @Column(name = "speaker_teacher_id")
    private Long speakerTeacherId;

    @Column(name = "speaker_position_snapshot", length = 255)
    private String speakerPositionSnapshot;

    @Column(name = "speaker_fio_snapshot", length = 255)
    private String speakerFioSnapshot;

    @Lob
    @Column(name = "speech_content")
    private String speechContent;

    @Lob
    @Column(name = "decision_text", nullable = false)
    private String decisionText;

    @Column(name = "votes_for", nullable = false)
    private int votesFor;

    @Column(name = "votes_against", nullable = false)
    private int votesAgainst;

    @Column(name = "votes_abstained", nullable = false)
    private int votesAbstained;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attachmentNumber ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PedagogicalCouncilAttachment> attachments = new ArrayList<>();
}
