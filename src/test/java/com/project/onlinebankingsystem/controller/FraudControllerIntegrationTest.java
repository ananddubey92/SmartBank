package com.project.onlinebankingsystem.controller;

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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class FraudControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;
    private TestingAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        auth = new TestingAuthenticationToken("rahul", "password", "ROLE_CUSTOMER");
        auth.setAuthenticated(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);

        session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    @Test
    void testFraudDetectionPage_WithAmount4000() throws Exception {
        mockMvc.perform(get("/fraud-detection")
                        .session(session)
                        .principal(auth)
                        .param("amount", "4000"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("simAmount", new BigDecimal("4000")))
                .andExpect(content().string(containsString("₹4,000")));
    }

    @Test
    void testFraudDetectionPage_WithAmount5000() throws Exception {
        mockMvc.perform(get("/fraud-detection")
                        .session(session)
                        .principal(auth)
                        .param("amount", "5000"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("simAmount", new BigDecimal("5000")))
                .andExpect(content().string(containsString("₹5,000")));
    }

    @Test
    void testFraudDetectionPage_WithAmount10000() throws Exception {
        mockMvc.perform(get("/fraud-detection")
                        .session(session)
                        .principal(auth)
                        .param("amount", "10000"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("simAmount", new BigDecimal("10000")))
                .andExpect(content().string(containsString("₹10,000")));
    }

    @Test
    void testFraudDetectionPage_DefaultAmount() throws Exception {
        mockMvc.perform(get("/fraud-detection")
                        .session(session)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(model().attribute("simAmount", new BigDecimal("5000")));
    }

    @Test
    void testFraudEvaluationApi_ExactAmount4000() throws Exception {
        mockMvc.perform(post("/api/fraud/evaluate")
                        .session(session)
                        .principal(auth)
                        .param("sourceAccountNumber", "1002345678")
                        .param("destinationAccountNumber", "2009876543")
                        .param("amount", "4000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferAmount").value(4000))
                .andExpect(jsonPath("$.riskScore").isNumber());
    }

    @Test
    void testFraudEvaluationApi_ExactAmount5000() throws Exception {
        mockMvc.perform(post("/api/fraud/evaluate")
                        .session(session)
                        .principal(auth)
                        .param("sourceAccountNumber", "1002345678")
                        .param("destinationAccountNumber", "2009876543")
                        .param("amount", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferAmount").value(5000))
                .andExpect(jsonPath("$.riskScore").isNumber());
    }

    @Test
    void testFraudEvaluationApi_ExactAmount10000() throws Exception {
        mockMvc.perform(post("/api/fraud/evaluate")
                        .session(session)
                        .principal(auth)
                        .param("sourceAccountNumber", "1002345678")
                        .param("destinationAccountNumber", "2009876543")
                        .param("amount", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferAmount").value(10000))
                .andExpect(jsonPath("$.riskScore").isNumber());
    }
}