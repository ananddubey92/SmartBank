package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.model.AuditLog;
import com.project.onlinebankingsystem.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog logAction(String performedBy,
                              String performedByRole,
                              String action,
                              String targetEntity,
                              String targetEntityId,
                              String previousState,
                              String newState,
                              String ipAddress,
                              String status,
                              String details) {
        AuditLog log = AuditLog.builder()
                .performedBy(performedBy != null ? performedBy : "SYSTEM")
                .performedByRole(performedByRole != null ? performedByRole : "ROLE_SUPER_ADMIN")
                .action(action)
                .targetEntity(targetEntity)
                .targetEntityId(targetEntityId)
                .previousState(previousState)
                .newState(newState)
                .ipAddress(ipAddress != null ? ipAddress : "127.0.0.1")
                .status(status != null ? status : "SUCCESS")
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
        return auditLogRepository.save(log);
    }

    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    public List<AuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }

    public List<AuditLog> getAuditLogsByEntity(String targetEntity) {
        return auditLogRepository.findByTargetEntityOrderByTimestampDesc(targetEntity);
    }
}