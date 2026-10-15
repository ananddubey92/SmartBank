package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.FraudAssessmentResponse;
import com.project.onlinebankingsystem.dto.FraudSimulationRequest;
import com.project.onlinebankingsystem.dto.TransactionResponse;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.service.FraudDetectionService;
import com.project.onlinebankingsystem.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fraud")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final com.project.onlinebankingsystem.repository.UserRepository userRepository;

    public FraudController(FraudDetectionService fraudDetectionService,
                           TransactionService transactionService,
                           TransactionRepository transactionRepository,
                           com.project.onlinebankingsystem.repository.UserRepository userRepository) {
        this.fraudDetectionService = fraudDetectionService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<FraudAssessmentResponse> evaluateTransfer(
            @RequestParam String sourceAccountNumber,
            @RequestParam String destinationAccountNumber,
            @RequestParam BigDecimal amount) {
        FraudAssessmentResponse response = fraudDetectionService.assessRisk(sourceAccountNumber, destinationAccountNumber, amount);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/simulate")
    public ResponseEntity<FraudAssessmentResponse> simulateFraud(@RequestBody FraudSimulationRequest request) {
        FraudAssessmentResponse response = fraudDetectionService.simulateCustom(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analysis/{transactionId}")
    public ResponseEntity<Map<String, Object>> getFraudAnalysis(@PathVariable Long transactionId, java.security.Principal principal) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));

        if (principal != null) {
            String username = principal.getName();
            com.project.onlinebankingsystem.model.User caller = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
            boolean isStaff = caller.getRole() == com.project.onlinebankingsystem.enums.Role.ROLE_ADMIN 
                    || caller.getRole() == com.project.onlinebankingsystem.enums.Role.ROLE_SUPER_ADMIN;
            boolean isSrcOwner = tx.getSourceAccount() != null && tx.getSourceAccount().getUser() != null 
                    && tx.getSourceAccount().getUser().getUsername().equalsIgnoreCase(username);
            boolean isDstOwner = tx.getDestinationAccount() != null && tx.getDestinationAccount().getUser() != null 
                    && tx.getDestinationAccount().getUser().getUsername().equalsIgnoreCase(username);
            if (!isStaff && !isSrcOwner && !isDstOwner) {
                throw new org.springframework.security.access.AccessDeniedException("Unauthorized access to fraud analysis for transaction " + transactionId);
            }
        }

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("transactionId", tx.getId());
        analysis.put("transactionReference", tx.getTransactionReference());
        analysis.put("amount", tx.getAmount());
        analysis.put("riskScore", tx.getRiskScore());
        analysis.put("riskLevel", tx.getRiskLevel() != null ? tx.getRiskLevel().name() : "LOW");
        analysis.put("fraudProbability", tx.getFraudProbability());
        analysis.put("decision", tx.getDecision() != null ? tx.getDecision().name() : "ALLOW");
        analysis.put("ruleBasedScore", tx.getRuleBasedScore());
        analysis.put("mlPredictionScore", tx.getMlPrediction());
        analysis.put("riskReasons", tx.getRiskReasons());
        analysis.put("fraudCheckedAt", tx.getFraudCheckedAt());
        analysis.put("status", tx.getStatus().name());

        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/alerts")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<TransactionResponse>> getFraudAlerts() {
        List<TransactionResponse> suspicious = transactionService.getAllTransactions().stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel())
                        || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())
                        || "BLOCKED".equalsIgnoreCase(t.getStatus())
                        || "UNDER_REVIEW".equalsIgnoreCase(t.getStatus()))
                .toList();
        return ResponseEntity.ok(suspicious);
    }

    @GetMapping("/metrics")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getFraudMetrics() {
        List<TransactionResponse> allTx = transactionService.getAllTransactions();
        long low = allTx.stream().filter(t -> "LOW".equalsIgnoreCase(t.getRiskLevel())).count();
        long medium = allTx.stream().filter(t -> "MEDIUM".equalsIgnoreCase(t.getRiskLevel())).count();
        long high = allTx.stream().filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())).count();
        long critical = allTx.stream().filter(t -> "CRITICAL".equalsIgnoreCase(t.getRiskLevel())).count();
        long blocked = allTx.stream().filter(t -> TransactionStatus.BLOCKED.name().equalsIgnoreCase(t.getStatus())).count();
        long underReview = allTx.stream().filter(t -> TransactionStatus.UNDER_REVIEW.name().equalsIgnoreCase(t.getStatus())).count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalEvaluated", allTx.size());
        metrics.put("lowRiskCount", low);
        metrics.put("mediumRiskCount", medium);
        metrics.put("highRiskCount", high);
        metrics.put("criticalRiskCount", critical);
        metrics.put("blockedCount", blocked);
        metrics.put("underReviewCount", underReview);

        return ResponseEntity.ok(metrics);
    }
}
