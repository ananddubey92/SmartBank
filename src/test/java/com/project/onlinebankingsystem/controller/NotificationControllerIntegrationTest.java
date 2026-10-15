package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.model.Notification;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.NotificationRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private MockHttpSession session;
    private TestingAuthenticationToken auth;
    private Long sampleNotificationId;

    @BeforeEach
    void setUp() {
        auth = new TestingAuthenticationToken("rahul", "password", "ROLE_CUSTOMER");
        auth.setAuthenticated(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);

        session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        User rahul = userRepository.findByUsername("rahul").orElse(null);
        assertNotNull(rahul, "User rahul must exist in database");

        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(rahul);
        if (!notifications.isEmpty()) {
            sampleNotificationId = notifications.get(0).getId();
        }
    }

    @Test
    void testNotificationsViewPage_HasUnreadCount() throws Exception {
        mockMvc.perform(get("/notifications")
                        .session(session)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("unreadCount"))
                .andExpect(model().attributeExists("notifications"));
    }

    @Test
    void testMarkAsRead_WithPost() throws Exception {
        if (sampleNotificationId != null) {
            mockMvc.perform(post("/api/notifications/" + sampleNotificationId + "/read")
                            .session(session)
                            .principal(auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Marked as read"));
        }
    }

    @Test
    void testMarkAsRead_WithPut() throws Exception {
        if (sampleNotificationId != null) {
            mockMvc.perform(put("/api/notifications/" + sampleNotificationId + "/read")
                            .session(session)
                            .principal(auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Marked as read"));
        }
    }

    @Test
    void testMarkAllAsRead_Endpoint() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all")
                        .session(session)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));
    }

    @Test
    void testMarkAllAsRead_AliasEndpoint() throws Exception {
        mockMvc.perform(post("/api/notifications/mark-all-read")
                        .session(session)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));
    }

    @Test
    void testGetUnreadCount() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .session(session)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber());
    }
}
