package com.project.onlinebankingsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.onlinebankingsystem.config.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DebitCardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Super Admin Backfill: Super Admin can trigger backfill successfully (200 OK)")
    void superAdmin_canTriggerBackfill_returns200() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");

        mockMvc.perform(post("/api/super-admin/debit-cards/backfill")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccountsScanned").isNumber())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Security: Regular Customer CANNOT access backfill endpoint (403 Forbidden)")
    void customer_cannotTriggerBackfill_returns403() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(post("/api/super-admin/debit-cards/backfill")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Regular Admin CANNOT access backfill endpoint (403 Forbidden)")
    void admin_cannotTriggerBackfill_returns403() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(post("/api/super-admin/debit-cards/backfill")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer self-service: Logged-in customer can retrieve their own debit card (200 OK)")
    void customer_canFetchOwnCard_returnsMaskedCard() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/api/debit-cards/my-card")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value(org.hamcrest.Matchers.startsWith("**** **** **** ")))
                .andExpect(jsonPath("$.cardHolderName").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.expiryDateFormatted").exists());
    }

    @Test
    @DisplayName("Security: Unauthenticated request to /my-card returns 401 Unauthorized")
    void unauthenticated_cannotFetchCard_returns401() throws Exception {
        mockMvc.perform(get("/api/debit-cards/my-card")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Customer self-service: Card response includes formatted card number and cvv for customer")
    void customer_cardIncludesFullNumberAndCvv() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/api/debit-cards/my-card")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formattedCardNumber").exists())
                .andExpect(jsonPath("$.rawCardNumber").exists())
                .andExpect(jsonPath("$.cvv").exists());
    }

    @Test
    @DisplayName("Customer self-service: Customer can toggle card status between ACTIVE and BLOCKED")
    void customer_canToggleCardStatus() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        // 1. Fetch own card ID
        String json = mockMvc.perform(get("/api/debit-cards/my-card")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number cardId = com.jayway.jsonpath.JsonPath.read(json, "$.id");

        // 2. Toggle status
        mockMvc.perform(post("/api/debit-cards/" + cardId + "/toggle-status")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // 3. Toggle back
        mockMvc.perform(post("/api/debit-cards/" + cardId + "/toggle-status")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Security: Unauthenticated request to toggle-status returns 401")
    void unauthenticated_cannotToggleCardStatus_returns401() throws Exception {
        mockMvc.perform(post("/api/debit-cards/1/toggle-status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
