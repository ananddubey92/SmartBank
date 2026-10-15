package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.NotificationResponse;
import com.project.onlinebankingsystem.enums.NotificationType;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Notification;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.NotificationRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Notification createNotification(User user, NotificationType type, String title, String message, String actionUrl) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return 0;
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long notificationId, String username) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));
        if (!notification.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("Unauthorized to access notification");
        }
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        notificationRepository.markAllAsReadForUser(user);
    }

    public NotificationResponse convertToResponse(Notification n) {
        String icon = "fa-bell";
        String color = "primary";

        switch (n.getType()) {
            case FRAUD_ALERT -> { icon = "fa-triangle-exclamation"; color = "danger"; }
            case TRANSACTION_BLOCKED -> { icon = "fa-ban"; color = "danger"; }
            case TRANSACTION_FAILED -> { icon = "fa-circle-xmark"; color = "warning"; }
            case TRANSACTION_SUCCESS -> { icon = "fa-circle-check"; color = "success"; }
            case OTP_REQUIRED -> { icon = "fa-shield-halved"; color = "warning"; }
            case BENEFICIARY_ADDED -> { icon = "fa-user-plus"; color = "info"; }
            case SECURITY_ALERT -> { icon = "fa-lock"; color = "danger"; }
            case LOGIN_ALERT -> { icon = "fa-right-to-bracket"; color = "info"; }
            case FINANCIAL_INSIGHT -> { icon = "fa-lightbulb"; color = "warning"; }
        }

        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .actionUrl(n.getActionUrl() != null ? n.getActionUrl() : "/dashboard")
                .icon(icon)
                .badgeColor(color)
                .build();
    }
}
