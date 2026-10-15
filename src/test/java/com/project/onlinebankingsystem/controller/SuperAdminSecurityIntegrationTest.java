package com.project.onlinebankingsystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.onlinebankingsystem.config.JwtUtils;
import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.AuditLog;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.AuditLogRepository;
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
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SuperAdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    // 1. Super Admin Form Login succeeds and redirects to /super-admin
    @Test
    void superAdminLogin_succeedsAndRedirectsToSuperAdminDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "superadmin")
                        .param("password", "SuperAdmin@123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin"));
    }

    // 2. Normal Admin Login succeeds and redirects to /admin
    @Test
    void normalAdminLogin_succeedsAndRedirectsToAdminDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "Admin@123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    // 3. Customer cannot access Super Admin Web (/super-admin) -> 403 Forbidden
    @Test
    void customer_cannotAccessSuperAdminWeb_returnsForbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/super-admin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    // 4. Normal Admin cannot access Super Admin Web (/super-admin) -> 403 Forbidden
    @Test
    void normalAdmin_cannotAccessSuperAdminWeb_returnsForbidden() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(get("/super-admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // 5. Unauthenticated user accessing Super Admin Web is redirected to /login
    @Test
    void unauthenticatedUser_isRedirectedFromSuperAdminWeb() throws Exception {
        mockMvc.perform(get("/super-admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    // 6. Customer cannot access Super Admin REST APIs -> 403 Forbidden
    @Test
    void customer_cannotAccessSuperAdminApi_returnsForbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        mockMvc.perform(get("/api/super-admin/dashboard")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 7. Normal Admin cannot access Super Admin REST APIs -> 403 Forbidden
    @Test
    void normalAdmin_cannotAccessSuperAdminApi_returnsForbidden() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(get("/api/super-admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // 8. Super Admin can access Super Admin Web Page -> 200 OK
    @Test
    void superAdmin_canAccessSuperAdminWebPage() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");

        mockMvc.perform(get("/super-admin")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(view().name("super-admin"))
                .andExpect(model().attributeExists("stats", "admins", "customers", "accounts", "transactions", "auditLogs"));
    }

    // 9. Super Admin can access Super Admin Dashboard API -> 200 OK with bank metrics
    @Test
    void superAdmin_canAccessDashboardApi() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");

        mockMvc.perform(get("/api/super-admin/dashboard")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").isNumber())
                .andExpect(jsonPath("$.totalAdmins").isNumber())
                .andExpect(jsonPath("$.totalAccounts").isNumber())
                .andExpect(jsonPath("$.totalLiquidity").isNumber());
    }

    // 10. Super Admin can provision a new Admin and audit log is recorded
    @Test
    void superAdmin_canProvisionAdmin_andAuditLogRecorded() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        long uniqueSeed = System.currentTimeMillis() % 10000000L;
        String newAdminUser = "adm" + uniqueSeed;

        CreateAdminRequest req = CreateAdminRequest.builder()
                .username(newAdminUser)
                .password("AdminSecret@123")
                .fullName("Provisioned Branch Admin")
                .email(newAdminUser + "@smartbank.ai")
                .phone("9" + String.format("%09d", uniqueSeed))
                .pan("PANAA" + String.format("%04d", uniqueSeed % 10000L) + "Z")
                .address("Operations Desk 4")
                .build();

        mockMvc.perform(post("/api/super-admin/admins")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(newAdminUser))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));

        Optional<User> created = userRepository.findByUsername(newAdminUser);
        assertTrue(created.isPresent());
        assertEquals(Role.ROLE_ADMIN, created.get().getRole());

        // Verify audit log exists for creation
        List<AuditLog> audits = auditLogRepository.findByActionOrderByTimestampDesc("SUPER_ADMIN_CREATED_ADMIN");
        assertFalse(audits.isEmpty());
        assertEquals("superadmin", audits.get(0).getPerformedBy());
    }

    // 11. Normal Admin CANNOT create an Admin via Super Admin API -> 403 Forbidden
    @Test
    void normalAdmin_cannotProvisionAdmin_returnsForbidden() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        CreateAdminRequest req = CreateAdminRequest.builder()
                .username("unauthadmin")
                .password("Secret@123")
                .fullName("Unauth Admin")
                .email("unauth@example.com")
                .phone("9888777666")
                .build();

        mockMvc.perform(post("/api/super-admin/admins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // 12. Super Admin can freeze and unfreeze bank accounts with audit logging
    @Test
    void superAdmin_canFreezeAndUnfreezeAccount_andAuditLogsRecorded() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        Account account = accountRepository.findAll().stream().findFirst().orElseThrow();
        String accNum = account.getAccountNumber();

        // 1. Freeze Account
        mockMvc.perform(patch("/api/super-admin/accounts/" + accNum + "/freeze")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        Account frozenAcc = accountRepository.findByAccountNumber(accNum).orElseThrow();
        assertEquals(AccountStatus.SUSPENDED, frozenAcc.getStatus());

        // 2. Unfreeze Account
        mockMvc.perform(patch("/api/super-admin/accounts/" + accNum + "/unfreeze")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Account unfrozenAcc = accountRepository.findByAccountNumber(accNum).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, unfrozenAcc.getStatus());

        // Check Audit Logs
        List<AuditLog> frozenLogs = auditLogRepository.findByActionOrderByTimestampDesc("ACCOUNT_FROZEN");
        assertFalse(frozenLogs.isEmpty());
        assertEquals(accNum, frozenLogs.get(0).getTargetEntityId());
    }

    // 13. Super Admin can block, review, and mark-safe transactions
    @Test
    void superAdmin_canManageTransactions_andAuditLogsRecorded() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        Transaction tx = transactionRepository.findAll().stream().findFirst().orElseThrow();
        String ref = tx.getTransactionReference();

        // Put under review
        mockMvc.perform(post("/api/super-admin/transactions/" + ref + "/review")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // Block
        mockMvc.perform(post("/api/super-admin/transactions/" + ref + "/block")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // Mark safe
        mockMvc.perform(post("/api/super-admin/transactions/" + ref + "/mark-safe")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify audit log
        List<AuditLog> safeLogs = auditLogRepository.findByActionOrderByTimestampDesc("TRANSACTION_MARKED_SAFE");
        assertFalse(safeLogs.isEmpty());
        assertEquals(ref, safeLogs.get(0).getTargetEntityId());
    }

    // 14. Registration strictly rejects 'superadmin' username
    @Test
    void registration_cannotUseSuperAdminUsername() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("superadmin")
                .password("FakeSuper@123")
                .fullName("Fake Super Admin")
                .email("fakesuper@example.com")
                .phone("9111222444")
                .dob(LocalDate.of(1990, 1, 1))
                .pan("ZZZZZ8888Z")
                .aadhar("999911113333")
                .gender("MALE")
                .address("123 Fake Lane")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("100.00"))
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // 15. Existing Admin functionality still works for 'admin'
    @Test
    void existingAdmin_canStillAccessAdminPortal() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        mockMvc.perform(get("/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"));

        mockMvc.perform(get("/api/admin/fraud-dashboard")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // 16. Verify users.enabled is mapped, persisted, and queried by Hibernate
    @Test
    void userEnabledField_isPersistedAndQueriedCorrectly() throws Exception {
        User superAdmin = userRepository.findByUsername("superadmin").orElseThrow();
        assertNotNull(superAdmin.getEnabled());
        assertTrue(superAdmin.getEnabled());

        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertNotNull(admin.getEnabled());
        assertTrue(admin.getEnabled());

        User customer = userRepository.findByUsername("rahul").orElseThrow();
        assertNotNull(customer.getEnabled());
        assertTrue(customer.getEnabled());
    }

    // 17. Super Admin can enable and disable users, and Spring Security enforces it
    @Test
    void superAdmin_canEnableAndDisableUser_andLoginEnforcesEnabled() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");

        // 1. Create a dedicated test operational admin
        String testAdminUser = "optestadmin";
        CreateAdminRequest req = CreateAdminRequest.builder()
                .username(testAdminUser)
                .password("OpAdmin@12345")
                .fullName("Operational Test Admin")
                .email("optest@smartbank.ai")
                .phone("9876543299")
                .build();

        mockMvc.perform(post("/api/super-admin/admins")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        User created = userRepository.findByUsername(testAdminUser).orElseThrow();
        assertTrue(created.getEnabled());

        // 2. Operational admin can log in while enabled
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", testAdminUser)
                        .param("password", "OpAdmin@12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        // 3. Super Admin suspends/disables this operational admin
        mockMvc.perform(patch("/api/super-admin/admins/" + testAdminUser + "/status")
                        .param("status", "SUSPENDED")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());

        User suspended = userRepository.findByUsername(testAdminUser).orElseThrow();
        assertFalse(suspended.getEnabled());
        assertEquals("SUSPENDED", suspended.getAccountSecurityStatus());

        // 4. Suspended admin CANNOT log in (Spring Security redirects to /login?error)
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", testAdminUser)
                        .param("password", "OpAdmin@12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?error*"));

        // 5. Super Admin re-enables this admin
        mockMvc.perform(patch("/api/super-admin/admins/" + testAdminUser + "/status")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());

        User reEnabled = userRepository.findByUsername(testAdminUser).orElseThrow();
        assertTrue(reEnabled.getEnabled());
        assertEquals("ACTIVE", reEnabled.getAccountSecurityStatus());

        // 6. Re-enabled admin can log in again
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", testAdminUser)
                        .param("password", "OpAdmin@12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    // 18. Super Admin can create account and audit log is recorded with opening balance
    @Test
    void superAdmin_canCreateAccount_andAuditLogRecorded() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        SuperAdminCreateAccountRequest req = SuperAdminCreateAccountRequest.builder()
                .customerUsername("rahul")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("750.00"))
                .ifscCode("OBIN0000101")
                .build();

        String responseJson = mockMvc.perform(post("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.accountType").value("SAVING"))
                .andExpect(jsonPath("$.balance").value(750.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userUsername").value("rahul"))
                .andReturn().getResponse().getContentAsString();

        AccountResponse resp = objectMapper.readValue(responseJson, AccountResponse.class);
        String newAccNum = resp.getAccountNumber();

        // Verify account exists in repository
        Optional<Account> accOpt = accountRepository.findByAccountNumber(newAccNum);
        assertTrue(accOpt.isPresent());
        assertEquals(0, new BigDecimal("750.00").compareTo(accOpt.get().getBalance()));

        // Verify audit log exists
        List<AuditLog> audits = auditLogRepository.findByActionOrderByTimestampDesc("ACCOUNT_CREATED");
        assertFalse(audits.isEmpty());
        assertEquals(newAccNum, audits.get(0).getTargetEntityId());
        assertEquals("superadmin", audits.get(0).getPerformedBy());
    }

    // 19. Super Admin cannot create account with negative initial balance
    @Test
    void superAdmin_cannotCreateAccount_withNegativeBalance() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        SuperAdminCreateAccountRequest req = SuperAdminCreateAccountRequest.builder()
                .customerUsername("rahul")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("-50.00"))
                .build();

        mockMvc.perform(post("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // 20. Super Admin cannot create account for non-existent customer
    @Test
    void superAdmin_cannotCreateAccount_forNonExistentCustomer() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        SuperAdminCreateAccountRequest req = SuperAdminCreateAccountRequest.builder()
                .customerUsername("nonexistentcustomer999")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("100.00"))
                .build();

        mockMvc.perform(post("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // 21. Super Admin cannot create account with duplicate account number
    @Test
    void superAdmin_cannotCreateAccount_withDuplicateAccountNumber() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        Account existing = accountRepository.findAll().stream().findFirst().orElseThrow();

        SuperAdminCreateAccountRequest req = SuperAdminCreateAccountRequest.builder()
                .customerUsername("rahul")
                .accountType("SAVING")
                .accountNumber(existing.getAccountNumber())
                .initialBalance(new BigDecimal("0.00"))
                .build();

        mockMvc.perform(post("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // 22. Super Admin can view complete account details
    @Test
    void superAdmin_canGetAccountDetails() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        Account account = accountRepository.findAll().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/super-admin/accounts/" + account.getAccountNumber())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(account.getAccountNumber()))
                .andExpect(jsonPath("$.accountType").value(account.getAccountType().name()))
                .andExpect(jsonPath("$.transactionCount").isNumber());
    }

    // 23. Super Admin can update account details and audit log is recorded
    @Test
    void superAdmin_canUpdateAccountDetails_andAuditLogRecorded() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        Account account = accountRepository.findAll().stream().findFirst().orElseThrow();
        String accNum = account.getAccountNumber();

        SuperAdminUpdateAccountRequest req = SuperAdminUpdateAccountRequest.builder()
                .accountHolderName("Updated Holder Name")
                .accountType("CURRENT")
                .ifscCode("OBIN0000999")
                .build();

        mockMvc.perform(put("/api/super-admin/accounts/" + accNum)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountHolderName").value("Updated Holder Name"))
                .andExpect(jsonPath("$.accountType").value("CURRENT"))
                .andExpect(jsonPath("$.ifscCode").value("OBIN0000999"));

        List<AuditLog> audits = auditLogRepository.findByActionOrderByTimestampDesc("ACCOUNT_UPDATED");
        assertFalse(audits.isEmpty());
        assertEquals(accNum, audits.get(0).getTargetEntityId());
    }

    // 24. Super Admin can activate and deactivate account
    @Test
    void superAdmin_canActivateAndDeactivateAccount() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        Account account = accountRepository.findAll().stream().findFirst().orElseThrow();
        String accNum = account.getAccountNumber();

        // Deactivate
        mockMvc.perform(patch("/api/super-admin/accounts/" + accNum + "/deactivate")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        Account suspended = accountRepository.findByAccountNumber(accNum).orElseThrow();
        assertEquals(AccountStatus.SUSPENDED, suspended.getStatus());

        // Activate
        mockMvc.perform(patch("/api/super-admin/accounts/" + accNum + "/activate")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Account active = accountRepository.findByAccountNumber(accNum).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, active.getStatus());
    }

    // 25. Super Admin can view account transaction history
    @Test
    void superAdmin_canViewAccountTransactions() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");
        Account account = accountRepository.findAll().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/super-admin/accounts/" + account.getAccountNumber() + "/transactions")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // 26. Super Admin safe deletion: closes account and preserves history if transactions exist
    @Test
    void superAdmin_canDeleteAccount_safelyClosesIfTransactionsExist() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");

        // Find an account that has transactions
        Account targetAcc = accountRepository.findAll().stream()
                .filter(a -> !transactionRepository.findAllByAccount(a).isEmpty())
                .findFirst().orElseThrow();
        String accNum = targetAcc.getAccountNumber();

        mockMvc.perform(delete("/api/super-admin/accounts/" + accNum)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.softDeleted").value(true));

        Account closedAcc = accountRepository.findByAccountNumber(accNum).orElseThrow();
        assertEquals(AccountStatus.CLOSED, closedAcc.getStatus());

        List<AuditLog> audits = auditLogRepository.findByActionOrderByTimestampDesc("ACCOUNT_CLOSED");
        assertFalse(audits.isEmpty());
        assertEquals(accNum, audits.get(0).getTargetEntityId());
    }

    // 27. Super Admin safe deletion: permanently deletes account if zero transactions and zero balance
    @Test
    void superAdmin_canDeleteAccount_permanentlyDeletesIfNoTransactions() throws Exception {
        String superAdminToken = jwtUtils.generateToken("superadmin");

        // Create an account with zero balance and zero transactions
        SuperAdminCreateAccountRequest req = SuperAdminCreateAccountRequest.builder()
                .customerUsername("rahul")
                .accountType("RD")
                .initialBalance(BigDecimal.ZERO)
                .build();

        String responseJson = mockMvc.perform(post("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        AccountResponse created = objectMapper.readValue(responseJson, AccountResponse.class);
        String newAccNum = created.getAccountNumber();

        // Delete account
        mockMvc.perform(delete("/api/super-admin/accounts/" + newAccNum)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.permanentlyDeleted").value(true));

        Optional<Account> deletedOpt = accountRepository.findByAccountNumber(newAccNum);
        assertFalse(deletedOpt.isPresent());

        List<AuditLog> audits = auditLogRepository.findByActionOrderByTimestampDesc("ACCOUNT_DELETED");
        assertFalse(audits.isEmpty());
        assertEquals(newAccNum, audits.get(0).getTargetEntityId());
    }

    // 28. Customer cannot access Super Admin Account endpoints -> 403 Forbidden
    @Test
    void customer_cannotAccessSuperAdminAccountEndpoints_returnsForbidden() throws Exception {
        String customerToken = jwtUtils.generateToken("rahul");

        SuperAdminCreateAccountRequest req = SuperAdminCreateAccountRequest.builder()
                .customerUsername("rahul")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("100.00"))
                .build();

        // Create account attempt
        mockMvc.perform(post("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Get all accounts attempt
        mockMvc.perform(get("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // Delete attempt
        mockMvc.perform(delete("/api/super-admin/accounts/1002345678")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    // 29. Normal Admin cannot access Super Admin Account endpoints -> 403 Forbidden
    @Test
    void normalAdmin_cannotAccessSuperAdminAccountEndpoints_returnsForbidden() throws Exception {
        String adminToken = jwtUtils.generateToken("admin");

        SuperAdminCreateAccountRequest req = SuperAdminCreateAccountRequest.builder()
                .customerUsername("rahul")
                .accountType("SAVING")
                .initialBalance(new BigDecimal("100.00"))
                .build();

        // Create account attempt
        mockMvc.perform(post("/api/super-admin/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Delete attempt
        mockMvc.perform(delete("/api/super-admin/accounts/1002345678")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}
