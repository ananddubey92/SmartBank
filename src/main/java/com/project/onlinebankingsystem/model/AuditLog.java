package com.project.onlinebankingsystem.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_target", columnList = "targetEntity, targetEntityId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String performedBy;

    @Column(nullable = false)
    private String performedByRole;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String targetEntity;

    @Column(nullable = false)
    private String targetEntityId;

    private String previousState;

    private String newState;

    private String ipAddress;

    @Builder.Default
    @Column(nullable = false)
    private String status = "SUCCESS";

    @Column(length = 1000)
    private String details;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = "SUCCESS";
        }
    }
}