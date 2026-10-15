package com.project.onlinebankingsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.onlinebankingsystem.config.JwtUtils;
import com.project.onlinebankingsystem.dto.AccountRequest;
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
public class AccountCreationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void customerUser_cannotCreateAccountViaApi_returns403Forbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");
        AccountRequest request = new AccountRequest("SAVING");

        mockMvc.perform(post("/api/accounts/create")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access Denied: Only Super Admin can open a new bank account."));
    }

    @Test
    void adminUser_cannotCreateAccountViaApi_returns403Forbidden() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");
        AccountRequest request = new AccountRequest("CURRENT");

        mockMvc.perform(post("/api/accounts/create")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access Denied: Only Super Admin can open a new bank account."));
    }

    @Test
    void adminUser_cannotAccessAccountCreateWebGet_returns403Forbidden() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(get("/accounts/create")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerUser_cannotAccessAccountCreateWebGet_returns403Forbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/accounts/create")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminUser_canCreateAccountViaApi() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        AccountRequest request = new AccountRequest("SAVING");

        mockMvc.perform(post("/api/accounts/create")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").exists())
                .andExpect(jsonPath("$.accountType").value("SAVING"));
    }
}
