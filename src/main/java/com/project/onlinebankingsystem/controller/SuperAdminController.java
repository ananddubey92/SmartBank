package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.model.AuditLog;
import com.project.onlinebankingsystem.service.AuditLogService;
import com.project.onlinebankingsystem.service.SuperAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final AuditLogService auditLogService;

    public SuperAdminController(SuperAdminService superAdminService, AuditLogService auditLogService) {
        this.superAdminService = superAdminService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SuperAdminDashboardDTO> getDashboard() {
        return ResponseEntity.ok(superAdminService.getDashboardMetrics());
    }

    // ================= ADMIN MANAGEMENT =================
    @GetMapping("/admins")
    public ResponseEntity<List<UserResponseDTO>> getAllAdmins() {
        return ResponseEntity.ok(superAdminService.getAllAdmins());
    }

    @PostMapping("/admins")
    public ResponseEntity<UserResponseDTO> createAdmin(@Valid @RequestBody CreateAdminRequest request,
                                            Principal principal,
                                            HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        UserResponseDTO created = superAdminService.createAdmin(request, actor, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/admins/{identifier}/status")
    public ResponseEntity<UserResponseDTO> updateAdminStatus(@PathVariable String identifier,
                                                  @RequestParam String status,
                                                  Principal principal,
                                                  HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.updateAdminStatus(identifier, status, actor, ip));
    }

    // ================= CUSTOMER MANAGEMENT =================
    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDTO>> getAllCustomers() {
        return ResponseEntity.ok(superAdminService.getAllCustomers());
    }

    @GetMapping("/users/{identifier}")
    public ResponseEntity<CustomerDetailResponse> getCustomerDetails(@PathVariable String identifier) {
        return ResponseEntity.ok(superAdminService.getCustomerDetails(identifier));
    }

    @PatchMapping("/users/{identifier}/status")
    public ResponseEntity<UserResponseDTO> updateCustomerStatus(@PathVariable String identifier,
                                                     @RequestParam String status,
                                                     Principal principal,
                                                     HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.updateCustomerStatus(identifier, status, actor, ip));
    }

    // ================= ACCOUNT MANAGEMENT =================
    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(superAdminService.getAllAccounts());
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody SuperAdminCreateAccountRequest request,
                                                         Principal principal,
                                                         HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        AccountResponse response = superAdminService.createAccount(request, actor, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/accounts/open")
    public ResponseEntity<SuperAdminAccountOpeningResponse> openNewAccount(@Valid @RequestBody SuperAdminOpenAccountRequest request,
                                                                           Principal principal,
                                                                           HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        SuperAdminAccountOpeningResponse response = superAdminService.openNewAccount(request, actor, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/accounts/{accountNumber}/approve")
    public ResponseEntity<AccountResponse> approveAccount(@PathVariable String accountNumber,
                                                          Principal principal,
                                                          HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.approveAccount(accountNumber, actor, ip));
    }

    @PostMapping("/accounts/{accountNumber}/reject")
    public ResponseEntity<AccountResponse> rejectAccount(@PathVariable String accountNumber,
                                                         @RequestParam(required = false) String reason,
                                                         Principal principal,
                                                         HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.rejectAccount(accountNumber, reason, actor, ip));
    }

    @PostMapping("/accounts/{accountNumber}/issue-card")
    public ResponseEntity<DebitCardResponse> issueDebitCard(@PathVariable String accountNumber,
                                                            Principal principal,
                                                            HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.issueDebitCardForAccount(accountNumber, actor, ip));
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountDetails(@PathVariable String accountNumber) {
        return ResponseEntity.ok(superAdminService.getAccountDetails(accountNumber));
    }

    @PutMapping("/accounts/{accountNumber}")
    public ResponseEntity<AccountResponse> updateAccount(@PathVariable String accountNumber,
                                                         @Valid @RequestBody SuperAdminUpdateAccountRequest request,
                                                         Principal principal,
                                                         HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.updateAccount(accountNumber, request, actor, ip));
    }

    @PatchMapping("/accounts/{accountNumber}/activate")
    public ResponseEntity<AccountResponse> activateAccount(@PathVariable String accountNumber,
                                                           Principal principal,
                                                           HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.activateAccount(accountNumber, actor, ip));
    }

    @PatchMapping("/accounts/{accountNumber}/deactivate")
    public ResponseEntity<AccountResponse> deactivateAccount(@PathVariable String accountNumber,
                                                             Principal principal,
                                                             HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.deactivateAccount(accountNumber, actor, ip));
    }

    @PatchMapping("/accounts/{accountNumber}/freeze")
    public ResponseEntity<AccountResponse> freezeAccount(@PathVariable String accountNumber,
                                                         Principal principal,
                                                         HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.freezeAccount(accountNumber, actor, ip));
    }

    @PatchMapping("/accounts/{accountNumber}/unfreeze")
    public ResponseEntity<AccountResponse> unfreezeAccount(@PathVariable String accountNumber,
                                                           Principal principal,
                                                           HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.unfreezeAccount(accountNumber, actor, ip));
    }

    @PatchMapping("/accounts/{accountNumber}/block")
    public ResponseEntity<AccountResponse> blockAccount(@PathVariable String accountNumber,
                                                        Principal principal,
                                                        HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.blockAccount(accountNumber, actor, ip));
    }

    @DeleteMapping("/accounts/{accountNumber}")
    public ResponseEntity<Map<String, Object>> closeOrDeleteAccount(@PathVariable String accountNumber,
                                                                    Principal principal,
                                                                    HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.closeOrDeleteAccount(accountNumber, actor, ip));
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public ResponseEntity<List<TransactionResponse>> getAccountTransactions(@PathVariable String accountNumber) {
        return ResponseEntity.ok(superAdminService.getAccountTransactions(accountNumber));
    }

    // ================= TRANSACTION & FRAUD MANAGEMENT =================
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(superAdminService.getAllTransactions());
    }

    @GetMapping("/fraud")
    public ResponseEntity<List<TransactionResponse>> getSuspiciousTransactions() {
        return ResponseEntity.ok(superAdminService.getSuspiciousTransactions());
    }

    @PostMapping("/transactions/{identifier}/block")
    public ResponseEntity<TransactionResponse> blockTransaction(@PathVariable String identifier,
                                                                Principal principal,
                                                                HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.blockTransaction(identifier, actor, ip));
    }

    @PostMapping("/transactions/{identifier}/review")
    public ResponseEntity<TransactionResponse> reviewTransaction(@PathVariable String identifier,
                                                                 Principal principal,
                                                                 HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.reviewTransaction(identifier, actor, ip));
    }

    @PostMapping("/transactions/{identifier}/mark-safe")
    public ResponseEntity<TransactionResponse> markTransactionSafe(@PathVariable String identifier,
                                                                   Principal principal,
                                                                   HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(superAdminService.markTransactionSafe(identifier, actor, ip));
    }

    // ================= AUDIT LOGS =================
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditLogService.getAllAuditLogs());
    }

    // ================= DEBIT CARD GOVERNANCE =================
    @PostMapping("/debit-cards/backfill")
    public ResponseEntity<DebitCardBackfillResultDTO> backfillDebitCards(Principal principal,
                                                                         HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        DebitCardBackfillResultDTO result = superAdminService.backfillDebitCards(actor, ip);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/debit-cards")
    public ResponseEntity<List<DebitCardResponse>> getAllDebitCards() {
        return ResponseEntity.ok(superAdminService.getAllDebitCards());
    }

    @PatchMapping("/debit-cards/{cardId}/status")
    public ResponseEntity<DebitCardResponse> updateDebitCardStatus(@PathVariable Long cardId,
                                                                   @Valid @RequestBody DebitCardStatusUpdateRequest request,
                                                                   Principal principal,
                                                                   HttpServletRequest servletRequest) {
        String actor = principal != null ? principal.getName() : "superadmin";
        String ip = servletRequest.getRemoteAddr();
        com.project.onlinebankingsystem.enums.CardStatus status = com.project.onlinebankingsystem.enums.CardStatus.valueOf(request.getStatus().trim().toUpperCase());
        DebitCardResponse updated = superAdminService.updateDebitCardStatus(cardId, status, actor, ip, request.getReason());
        return ResponseEntity.ok(updated);
    }
}