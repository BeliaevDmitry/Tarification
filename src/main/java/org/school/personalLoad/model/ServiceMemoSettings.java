package org.school.personalLoad.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "service_memo_settings")
public class ServiceMemoSettings {
    @Id
    @Column(name = "id")
    private Long id = 1L;

    @Column(name = "director_title", nullable = false, length = 255)
    private String directorTitle = "Директору ГБОУ Школы №7";

    @Column(name = "director_name", nullable = false, length = 255)
    private String directorName = "Ждановой И.Д.";
}
