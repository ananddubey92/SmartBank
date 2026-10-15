package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.FraudAssessmentResponse;
import com.project.onlinebankingsystem.dto.FraudAssessmentResponse.RiskFactorItem;
import com.project.onlinebankingsystem.dto.FraudSimulationRequest;
import com.project.onlinebankingsystem.enums.FraudDecision;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Beneficiary;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.BeneficiaryRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class FraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final MlFraudServiceClient mlFraudServiceClient;

    public FraudDetectionService(TransactionRepository transactionRepository,
                                 BeneficiaryRepository beneficiaryRepository,
                                 AccountRepository accountRepository,
                                 MlFraudServiceClient mlFraudServiceClient) {
        this.transactionRepository = transactionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.accountRepository = accountRepository;
        this.mlFraudServiceClient = mlFraudServiceClient;
    }

    /**
     * Hybrid Intelligent Risk Assessment combining deterministic rule-based vectors
     * and real Machine Learning fraud inference.
     */
    public FraudAssessmentResponse assessRisk(String sourceAccountNumber,
                                              String destinationAccountNumber,
                                              BigDecimal amount) {

        Account sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber).orElse(null);
        if (sourceAccount == null) {
            return FraudAssessmentResponse.builder()
                    .riskScore(95)
                    .riskLevel(RiskLevel.HIGH.name())
                    .fraudProbability(95.0)
                    .ruleBasedScore(95)
                    .mlPredictionScore(95)
                    .decision(FraudDecision.BLOCK.name())
                    .reasons(List.of("Source account not found or invalid"))
                    .requiresOtp(true)
                    .simulatedOtp("123456")
                    .transferAmount(amount)
                    .sourceAccountNumber(sourceAccountNumber)
                    .destinationAccountNumber(destinationAccountNumber)
                    .build();
        }

        User user = sourceAccount.getUser();

        // 1. Extract historical transactions for feature engineering
        List<Transaction> pastTransactions = transactionRepository.findAllByAccount(sourceAccount);
        List<BigDecimal> pastTransferAmounts = pastTransactions.stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER &&
                        (t.getStatus() == TransactionStatus.SUCCESS || t.getStatus() == TransactionStatus.COMPLETED))
                .filter(t -> t.getSourceAccount() != null && t.getSourceAccount().getAccountNumber().equals(sourceAccountNumber))
                .map(Transaction::getAmount)
                .toList();

        BigDecimal avgTransferAmount = BigDecimal.valueOf(5000.0);
        double deviationMultiplier = 1.0;

        if (!pastTransferAmounts.isEmpty()) {
            BigDecimal sum = pastTransferAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            avgTransferAmount = sum.divide(BigDecimal.valueOf(pastTransferAmounts.size()), 2, RoundingMode.HALF_UP);
            if (avgTransferAmount.compareTo(BigDecimal.ZERO) > 0) {
                deviationMultiplier = amount.divide(avgTransferAmount, 2, RoundingMode.HALF_UP).doubleValue();
            }
        } else {
            avgTransferAmount = amount;
        }

        // 2. Beneficiary Novelty & Prior History Check
        String username = user != null ? user.getUsername() : null;
        List<Beneficiary> beneficiaries = username != null
                ? beneficiaryRepository.findByUserUsername(username)
                : List.of();

        Optional<Beneficiary> matchingBen = beneficiaries.stream()
                .filter(b -> b.getAccountNumber().equalsIgnoreCase(destinationAccountNumber))
                .findFirst();

        boolean isNewBeneficiary = false;
        if (matchingBen.isPresent()) {
            Beneficiary ben = matchingBen.get();
            if (ben.getCreatedAt() != null && ben.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
                isNewBeneficiary = true;
            } else if (ben.getTransferCount() == null || ben.getTransferCount() == 0) {
                isNewBeneficiary = true;
            }
        } else {
            isNewBeneficiary = true; // External recipient not saved
        }

        long prevTransfersToRecipient = pastTransactions.stream()
                .filter(t -> t.getDestinationAccount() != null
                        && t.getDestinationAccount().getAccountNumber().equalsIgnoreCase(destinationAccountNumber)
                        && (t.getStatus() == TransactionStatus.SUCCESS || t.getStatus() == TransactionStatus.COMPLETED))
                .count();

        // 3. Velocity / Frequency in last 10 minutes
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        int recentTransfersIn10Min = (int) pastTransactions.stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER)
                .filter(t -> t.getTimestamp().isAfter(tenMinutesAgo))
                .count();

        // 4. Temporal Anomaly
        LocalTime now = LocalTime.now();
        int transactionHour = now.getHour();
        boolean isOvernight = (transactionHour >= 0 && transactionHour < 5);

        // 5. Balance Depletion Percentage
        double balanceDepletionPct = 0.0;
        if (sourceAccount.getBalance() != null && sourceAccount.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            balanceDepletionPct = amount.divide(sourceAccount.getBalance(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        // 6. Account Age in months
        int accountAgeMonths = 12;
        if (sourceAccount.getCreatedAt() != null) {
            accountAgeMonths = (int) ChronoUnit.MONTHS.between(sourceAccount.getCreatedAt(), LocalDateTime.now());
            if (accountAgeMonths < 1) accountAgeMonths = 1;
        }

        // 7. Security History
        int prevSuspiciousCount = (int) pastTransactions.stream()
                .filter(t -> t.getRiskLevel() == RiskLevel.HIGH || t.getRiskLevel() == RiskLevel.CRITICAL
                        || t.getStatus() == TransactionStatus.BLOCKED)
                .count();

        int failedLogins = (user != null && user.getFailedLoginAttempts() != null) ? user.getFailedLoginAttempts() : 0;

        // --- Execute Hybrid Evaluation ---
        return evaluateHybridRisk(
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                avgTransferAmount,
                deviationMultiplier,
                isNewBeneficiary,
                (int) prevTransfersToRecipient,
                recentTransfersIn10Min,
                transactionHour,
                isOvernight,
                balanceDepletionPct,
                accountAgeMonths,
                prevSuspiciousCount,
                failedLogins
        );
    }

    /**
     * Executes custom simulation for the Fraud Detection Lab.
     */
    public FraudAssessmentResponse simulateCustom(FraudSimulationRequest req) {
        BigDecimal amount = req.getAmount() != null ? req.getAmount() : BigDecimal.valueOf(5000);
        BigDecimal avgAmount = BigDecimal.valueOf(5000);
        double deviationMultiplier = amount.divide(avgAmount, 2, RoundingMode.HALF_UP).doubleValue();

        boolean newBen = Boolean.TRUE.equals(req.getNewBeneficiary());
        int prevTransfers = Boolean.TRUE.equals(req.getPreviousTransfers()) ? 5 : 0;
        int freq10m = req.getFrequencyIn10Min() != null ? req.getFrequencyIn10Min() : 0;

        int hour = 14;
        if (req.getTransactionTime() != null && req.getTransactionTime().contains(":")) {
            try {
                hour = Integer.parseInt(req.getTransactionTime().split(":")[0].trim());
            } catch (Exception ignored) {}
        }
        boolean isOvernight = (hour >= 0 && hour < 5);

        double depletionPct = req.getBalanceDepletionPercent() != null ? req.getBalanceDepletionPercent() : 15.0;
        int accountAge = req.getAccountAgeMonths() != null ? req.getAccountAgeMonths() : 12;
        int prevSusp = Boolean.TRUE.equals(req.getPreviousSuspiciousActivity()) ? 2 : 0;

        String src = req.getSourceAccountNumber() != null ? req.getSourceAccountNumber() : "1002345678";
        String dest = req.getDestinationAccountNumber() != null ? req.getDestinationAccountNumber() : "2009876543";

        return evaluateHybridRisk(
                src, dest, amount, avgAmount, deviationMultiplier,
                newBen, prevTransfers, freq10m, hour, isOvernight,
                depletionPct, accountAge, prevSusp, 0
        );
    }

    private FraudAssessmentResponse evaluateHybridRisk(String sourceAccountNumber,
                                                       String destinationAccountNumber,
                                                       BigDecimal amount,
                                                       BigDecimal avgTransferAmount,
                                                       double deviationMultiplier,
                                                       boolean isNewBeneficiary,
                                                       int prevTransfersToRecipient,
                                                       int recentTransfersIn10Min,
                                                       int transactionHour,
                                                       boolean isOvernight,
                                                       double balanceDepletionPct,
                                                       int accountAgeMonths,
                                                       int prevSuspiciousCount,
                                                       int failedLogins) {

        int ruleScore = 5; // Clean baseline
        List<String> reasons = new ArrayList<>();
        List<RiskFactorItem> factorBreakdown = new ArrayList<>();

        // 1. Amount & Deviation Rules
        if (deviationMultiplier >= 4.0) {
            ruleScore += 35;
            String desc = String.format("Amount is %.1fx higher than your typical transfer (Avg: ₹%s)",
                    deviationMultiplier, formatAmount(avgTransferAmount));
            reasons.add(desc);
            factorBreakdown.add(new RiskFactorItem("Large Amount Deviation", 35, desc));
        } else if (deviationMultiplier >= 2.5) {
            ruleScore += 22;
            String desc = String.format("Higher than normal amount: %.1fx historical transfer average", deviationMultiplier);
            reasons.add(desc);
            factorBreakdown.add(new RiskFactorItem("Elevated Transfer Amount", 22, desc));
        } else if (deviationMultiplier >= 1.8) {
            ruleScore += 12;
            reasons.add("Transaction amount moderately above typical history");
            factorBreakdown.add(new RiskFactorItem("Moderate Amount Deviation", 12, "Moderately above typical history"));
        }

        if (amount.compareTo(BigDecimal.valueOf(100000)) >= 0) {
            ruleScore += 25;
            reasons.add(String.format("High institutional monitoring threshold exceeded: ₹%s", formatAmount(amount)));
            factorBreakdown.add(new RiskFactorItem("Institutional High Value", 25, "Exceeds ₹1,00,000 threshold"));
        } else if (amount.compareTo(BigDecimal.valueOf(50000)) >= 0) {
            ruleScore += 15;
            reasons.add(String.format("Transaction exceeds verification threshold: ₹%s", formatAmount(amount)));
            factorBreakdown.add(new RiskFactorItem("Standard High Value Threshold", 15, "Exceeds ₹50,000 threshold"));
        }

        // 2. Beneficiary Novelty Rules
        if (isNewBeneficiary) {
            ruleScore += 20;
            reasons.add("New or unsaved beneficiary detected");
            factorBreakdown.add(new RiskFactorItem("New Beneficiary", 20, "Recipient added within 24h or unsaved node"));
        }
        if (prevTransfersToRecipient == 0) {
            ruleScore += 15;
            reasons.add("First-time transfer to this recipient account");
            factorBreakdown.add(new RiskFactorItem("First-Time Destination Transfer", 15, "No prior completed transfers to recipient"));
        }

        // 3. Velocity / Frequency Rules
        if (recentTransfersIn10Min >= 3) {
            ruleScore += 28;
            String desc = String.format("High velocity spike: %d transfers attempted in the last 10 minutes", recentTransfersIn10Min + 1);
            reasons.add(desc);
            factorBreakdown.add(new RiskFactorItem("High Velocity Spike", 28, desc));
        } else if (recentTransfersIn10Min >= 1) {
            ruleScore += 15;
            reasons.add("Repeated transaction within rapid 10-minute window");
            factorBreakdown.add(new RiskFactorItem("Rapid Successive Transfer", 15, "Multiple transfers in short window"));
        }

        // 4. Temporal Anomaly
        if (isOvernight) {
            ruleScore += 15;
            String desc = String.format("Unusual transaction time: initiated during overnight hours (%02d:00)", transactionHour);
            reasons.add(desc);
            factorBreakdown.add(new RiskFactorItem("Unusual Transaction Time", 15, desc));
        }

        // 5. Balance Depletion Rules
        if (balanceDepletionPct >= 85.0) {
            ruleScore += 25;
            String desc = String.format("High balance depletion: transfer drains %.1f%% of total available balance", balanceDepletionPct);
            reasons.add(desc);
            factorBreakdown.add(new RiskFactorItem("Balance Depletion Anomaly", 25, desc));
        } else if (balanceDepletionPct >= 60.0) {
            ruleScore += 12;
            String desc = String.format("Noticeable balance depletion (%.1f%% of available balance)", balanceDepletionPct);
            reasons.add(desc);
            factorBreakdown.add(new RiskFactorItem("Noticeable Balance Depletion", 12, desc));
        }

        // 6. Security History Rules
        if (prevSuspiciousCount > 0) {
            ruleScore += 12;
            reasons.add("Previous suspicious transactions logged on account history");
            factorBreakdown.add(new RiskFactorItem("Prior Suspicious History", 12, "Account previously triggered alerts"));
        }
        if (failedLogins > 0) {
            ruleScore += 10;
            reasons.add(String.format("Recent failed login attempts detected (%d)", failedLogins));
            factorBreakdown.add(new RiskFactorItem("Recent Authentication Failures", 10, "Multiple bad credential attempts logged"));
        }

        ruleScore = Math.min(100, Math.max(5, ruleScore));

        // Call ML Microservice (or fallback to Java ML engine)
        Map<String, Object> mlResult = mlFraudServiceClient.predict(
                amount,
                avgTransferAmount,
                deviationMultiplier,
                recentTransfersIn10Min,
                isNewBeneficiary,
                prevTransfersToRecipient,
                transactionHour,
                isOvernight,
                balanceDepletionPct,
                accountAgeMonths,
                prevSuspiciousCount,
                failedLogins
        );

        double mlFraudProbability = (double) mlResult.getOrDefault("fraudProbability", (double) ruleScore);
        int mlRiskScore = (int) mlResult.getOrDefault("mlRiskScore", ruleScore);
        boolean mlConnected = Boolean.TRUE.equals(mlResult.get("connected"));

        // Hybrid Risk Score Combination:
        // Final Risk Score = round(0.45 * RuleRiskScore + 0.55 * MLFraudProbability)
        int finalRiskScore = (int) Math.round((0.45 * ruleScore) + (0.55 * mlFraudProbability));
        finalRiskScore = Math.min(100, Math.max(5, finalRiskScore));

        // Risk Level Classification:
        // 0–30: LOW
        // 31–60: MEDIUM
        // 61–80: HIGH
        // 81–100: CRITICAL
        RiskLevel riskLevel;
        FraudDecision decision;

        if (finalRiskScore <= 30) {
            riskLevel = RiskLevel.LOW;
            decision = FraudDecision.ALLOW;
        } else if (finalRiskScore <= 60) {
            riskLevel = RiskLevel.MEDIUM;
            decision = FraudDecision.REVIEW;
        } else if (finalRiskScore <= 80) {
            riskLevel = RiskLevel.HIGH;
            decision = FraudDecision.REVIEW;
        } else {
            riskLevel = RiskLevel.CRITICAL;
            decision = FraudDecision.BLOCK;
        }

        boolean requiresOtp = (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL);

        if (reasons.isEmpty()) {
            reasons.add("Consistent with verified historical activity and standard recipient.");
        }
        if (factorBreakdown.isEmpty()) {
            factorBreakdown.add(new RiskFactorItem("Verified Baseline", 0, "No elevated risk factors identified"));
        }

        return FraudAssessmentResponse.builder()
                .riskScore(finalRiskScore)
                .riskLevel(riskLevel.name())
                .fraudProbability(mlFraudProbability)
                .ruleBasedScore(ruleScore)
                .mlPredictionScore(mlRiskScore)
                .decision(decision.name())
                .reasons(reasons)
                .factorBreakdown(factorBreakdown)
                .requiresOtp(requiresOtp)
                .transferAmount(amount)
                .sourceAccountNumber(sourceAccountNumber)
                .destinationAccountNumber(destinationAccountNumber)
                .simulatedOtp("482910")
                .mlServiceConnected(mlConnected)
                .build();
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%,.2f", amount);
    }
}
