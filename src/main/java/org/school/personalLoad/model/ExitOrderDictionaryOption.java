package org.school.personalLoad.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "exit_order_dictionary_option", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exit_order_dictionary_type_value", columnNames = {"option_type", "option_value"})
}, indexes = @Index(name = "idx_exit_order_dictionary_type_sort", columnList = "option_type,sort_order"))
public class ExitOrderDictionaryOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 40)
    private ExitOrderDictionaryType type;

    @Column(name = "option_value", nullable = false, length = 2000)
    private String value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
