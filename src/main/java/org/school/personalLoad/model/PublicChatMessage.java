package org.school.personalLoad.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "public_chat_message", indexes = {
        @Index(name = "idx_public_chat_message_created_at", columnList = "createdAt")
})
@Getter
@Setter
public class PublicChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String author;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(nullable = false)
    private Instant createdAt;
}
