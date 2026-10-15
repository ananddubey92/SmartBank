package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.NotificationResponse;
import com.project.onlinebankingsystem.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(Principal principal) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(principal.getName()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Principal principal) {
        long count = notificationService.getUnreadCount(principal.getName());
        Map<String, Object> map = new HashMap<>();
        map.put("unreadCount", count);
        return ResponseEntity.ok(map);
    }

    @RequestMapping(value = "/{id}/read", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Map<String, String>> markAsRead(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }
        notificationService.markAsRead(id, principal.getName());
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @RequestMapping(value = {"/read-all", "/mark-all-read"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Map<String, String>> markAllAsRead(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }
        notificationService.markAllAsRead(principal.getName());
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
