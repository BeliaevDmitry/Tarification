package org.school.ordergen.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class OrderData {
    private String eventDate;
    private String className;
    private String number;
    private String venue;
    private String address;
    private String eventTime;
    private String leader;          // винительный для первого пункта
    private String deputy;           // винительный для заместителя (с предлогом или пусто)
    private String leaderName;       // именительный (полное ФИО первого учителя) – возможно, уже не нужен, если заменим на дательный
    private String leaderDative;     // дательный для обращения (пункты 3,5)
    private String gatheringTime;
    private String gatheringPlace;
    private String returnTime;
    private String curator;
    private List<StudentInfo> students;
    private String accompanying;     // именительный + телефон
    private String classWord; // "класса" или "классов"
    private String accompanyingTitle; // "Сопровождающий" или "Сопровождающие"
}