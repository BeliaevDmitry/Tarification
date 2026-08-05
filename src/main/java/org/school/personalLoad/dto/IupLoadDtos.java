package org.school.personalLoad.dto;

import lombok.Data;
import org.school.personalLoad.model.IupDeliveryForm;
import org.school.personalLoad.model.IupStatus;
import org.school.personalLoad.model.StudentCategory;
import org.school.personalLoad.model.StudyPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class IupLoadDtos {

    private IupLoadDtos() {
    }

    @Data
    public static class Row {
        private Long manualLoadEntryId;
        private Long iupPlanId;
        private Long assignmentId;
        private Long studentId;
        private String studentFullName;
        private StudentCategory studentCategory;
        private Long teacherId;
        private String teacherFullName;
        private String subjectName;
        private String className;
        private String baseClassName;
        private String numberSchoolBuilding;
        private BigDecimal hoursPerWeek;
        private StudyPeriod studyPeriod;
        private IupDeliveryForm deliveryForm;
        private LocalDate validFrom;
        private LocalDate validTo;
        private IupStatus iupStatus;
        private String orderNumber;
        private LocalDate orderDate;
        private BigDecimal subjectCoefficient;
        private BigDecimal categoryCoefficient;
        private BigDecimal preliminaryMonthlyAmount;
        private boolean activeNow;
    }
}
