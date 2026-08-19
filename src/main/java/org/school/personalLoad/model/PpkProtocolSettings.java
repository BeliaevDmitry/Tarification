package org.school.personalLoad.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "ppk_protocol_settings")
public class PpkProtocolSettings {
    public static final long DEFAULT_ID = 1L;
    public static final String DEFAULT_CHAIR = "Власова Ю.С.";
    public static final String DEFAULT_SECRETARY = "Рыбкина Л.П.";
    public static final String DEFAULT_ATTENDEES = String.join("\n",
            "Дмитриева Ирина Николаевна",
            "Белкина Анастасия Андреевна",
            "Сонина Елена Анатольевна",
            "Грачев Глеб Михайлович"
    );

    @Id
    @Column(name = "id")
    private Long id = DEFAULT_ID;

    @Column(name = "chair_name", nullable = false, length = 500)
    private String chairName = DEFAULT_CHAIR;

    @Column(name = "chair_employee_id")
    private Long chairEmployeeId;

    @Column(name = "chair_position", length = 500)
    private String chairPosition;

    @Column(name = "secretary_name", nullable = false, length = 500)
    private String secretaryName = DEFAULT_SECRETARY;

    @Column(name = "secretary_employee_id")
    private Long secretaryEmployeeId;

    @Column(name = "secretary_position", length = 500)
    private String secretaryPosition;

    @Column(name = "attendees", nullable = false, length = 4000)
    private String attendees = DEFAULT_ATTENDEES;

    @Column(name = "attendee_employee_ids", length = 4000)
    private String attendeeEmployeeIds;
}
