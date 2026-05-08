package org.school.personalLoad.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_action_log", indexes = {
        @Index(name = "idx_user_action_log_created_at", columnList = "createdAt"),
        @Index(name = "idx_user_action_log_user", columnList = "userId")
})
@Getter
@Setter
public class UserActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String username;
    private String fullName;
    private String role;
    private String actionType;
    private String entityType;
    @Column(length = 2000)
    private String details;
    private String ip;
    @Column(length = 512)
    private String userAgent;
    private Integer statusCode;
    private boolean success;
    private Instant createdAt;
}
