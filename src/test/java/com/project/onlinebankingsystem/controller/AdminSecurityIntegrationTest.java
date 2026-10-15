package com.project.onlinebankingsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.onlinebankingsystem.config.JwtUtils;
import com.project.onlinebankingsystem.dto.RegisterRequest;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticatedUser_isRedirectedFromAdminWeb() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void unauthenticatedUser_isRejectedFromAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void formLogin_redirectsAdminToAdminConsole() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "Admin@123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void formLogin_redirectsCustomerToDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "rahul")
                        .param("password", "Rahul@123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void customerUser_cannotAccessAdminWebPage_returnsForbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/admin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerUser_cannotAccessAdminApi_returnsForbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void customerUser_cannotAccessFraudDashboard_returnsForbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/api/admin/fraud-dashboard")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUser_canAccessAdminWebPage() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(get("/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(model().attributeExists("users", "accounts", "transactions", "totalLiquidity"));
    }

    @Test
    void adminUser_canAccessAdminApis() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/admin/fraud-dashboard")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").isNumber())
                .andExpect(jsonPath("$.totalCustomers").isNumber())
                .andExpect(jsonPath("$.totalLiquidity").isNumber());
    }

    @Test
    void adminUser_canInspectCustomerDetails() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(get("/api/admin/users/rahul")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("rahul"))
                .andExpect(jsonPath("$.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.accounts").isArray());
    }

    @Test
    void adminUser_canManageTransactionsUsingReferenceString() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        Transaction tx = transactionRepository.findAll().stream().findFirst().orElseThrow();
        String ref = tx.getTransactionReference();

        // 1. Put Under Review
        mockMvc.perform(post("/api/admin/transactions/" + ref + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value(ref))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // 2. Block Transaction
        mockMvc.perform(post("/api/admin/transactions/" + ref + "/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value(ref))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // 3. Mark Transaction Safe
        mockMvc.perform(post("/api/admin/transactions/" + ref + "/mark-safe")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value(ref))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void registration_cannotUseAdminUsername() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("admin")
                .password("Test@123")
                .fullName("Fake Admin")
                .email("fakeadmin@example.com")
                .phone("9111222333")
                .dob(LocalDate.of(1990, 1, 1))
                .pan("ZZZZZ9999Z")
                .aadhar("999911112222")
                .gender("MALE")
                .address("123 Fake Street")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("100.00"))
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registration_alwaysAssignsCustomerRole() throws Exception {
        long seed = System.currentTimeMillis();
        String testUser = "usr" + (seed % 10000000L);
        RegisterRequest request = RegisterRequest.builder()
                .username(testUser)
                .password("Password@123")
                .fullName("New Test Customer")
                .email(testUser + "@testmail.com")
                .phone("9" + String.format("%09d", (seed + 111) % 1000000000L))
                .dob(LocalDate.of(1995, 5, 20))
                .pan("ABCDE" + String.format("%04d", (seed + 222) % 10000L) + "F")
                .aadhar("8" + String.format("%011d", (seed + 333) % 100000000000L))
                .gender("MALE")
                .address("77 Test Colony, Bangalore")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("500.00"))
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUser));

        Optional<User> createdUser = userRepository.findByUsername(testUser);
        assertTrue(createdUser.isPresent());
        assertEquals(Role.ROLE_CUSTOMER, createdUser.get().getRole());
    }
}