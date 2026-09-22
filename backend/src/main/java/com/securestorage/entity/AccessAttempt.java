package com.securestorage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "access_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String ip;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String status;

    private String reason;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
