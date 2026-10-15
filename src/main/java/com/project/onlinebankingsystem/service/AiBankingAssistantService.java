package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.AiAssistantRequest;
import com.project.onlinebankingsystem.dto.AiAssistantResponse;
import com.project.onlinebankingsystem.dto.FinancialHealthResponse;
import com.project.onlinebankingsystem.dto.TransactionResponse;
import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiBankingAssistantService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FinancialHealthService financialHealthService;

    public AiBankingAssistantService(UserRepository userRepository,
                                     AccountRepository accountRepository,
                                     TransactionRepository transactionRepository,
                                     FinancialHealthService financialHealthService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.financialHealthService = financialHealthService;
    }

    public AiAssistantResponse processQuery(String username, String query) {
        return processQuery(username, new AiAssistantRequest(query));
    }

    public AiAssistantResponse processQuery(String username, AiAssistantRequest request) {
        String query = request != null && request.getQuery() != null ? request.getQuery().trim() : "";
        String lower = query.toLowerCase();

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return AiAssistantResponse.builder()
                    .answer("Unable to identify account holder. Please ensure you are logged in.")
                    .confidence(0.0)
                    .intentDetected("UNKNOWN_USER")
                    .timestamp(LocalDateTime.now())
                    .engineUsed("SmartBank Local Intent Engine")
                    .build();
        }

        List<Account> accounts = accountRepository.findByUserUsername(username);
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Transaction> userTransactions;
        if (accounts.isEmpty()) {
            userTransactions = Collections.emptyList();
        } else if (accounts.size() == 1) {
            userTransactions = transactionRepository.findAllByAccount(accounts.get(0));
            if (userTransactions == null || userTransactions.isEmpty()) {
                List<Transaction> byAccounts = transactionRepository.findAllByAccounts(accounts);
                if (byAccounts != null && !byAccounts.isEmpty()) {
                    userTransactions = byAccounts;
                }
            }
        } else {
            userTransactions = transactionRepository.findAllByAccounts(accounts);
        }
        if (userTransactions == null) {
            userTransactions = Collections.emptyList();
        }

        // 1. Balance & Accounts Intent
        if (containsAny(lower, "balance", "how much money", "account balance", "funds available")) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Hello %s! Your total combined liquid balance across %d account(s) is **₹%s**.\n\n",
                    user.getFullName(), accounts.size(), formatAmount(totalBalance)));
            for (Account acc : accounts) {
                sb.append(String.format("• **%s** (%s): ₹%s (IFSC: `%s`)\n",
                        acc.getAccountNumber(), acc.getAccountType(), formatAmount(acc.getBalance()), acc.getIfscCode()));
            }
            return buildResponse(sb.toString(), "BALANCE_INQUIRY", 0.98,
                    List.of("How much did I spend this month?", "Show my last 5 transactions", "What is my financial health score?"),
                    "/accounts");
        }

        // 2. Blocked Transaction Reason Intent ("Why was my transaction blocked?")
        if (containsAny(lower, "blocked", "declined", "rejected", "why was my", "failed transfer")) {
            Optional<Transaction> blockedTx = userTransactions.stream()
                    .filter(t -> t.getStatus() == TransactionStatus.BLOCKED || (t.getRiskScore() != null && t.getRiskScore() >= 80))
                    .max(Comparator.comparing(Transaction::getTimestamp));

            if (blockedTx.isPresent()) {
                Transaction b = blockedTx.get();
                String ans = String.format(
                        "Your transaction **%s** for **₹%s** was flagged or blocked by SmartBank AI's security engine.\n\n" +
                        "• **Calculated Risk Score:** %d / 100 (%s Risk)\n" +
                        "• **Fraud Probability:** %.1f%%\n" +
                        "• **Reason Flagged:** %s\n\n" +
                        "💡 *To complete high-risk transfers safely, verify with your 6-digit OTP or 4-digit transaction PIN in the Transfer portal.*",
                        b.getTransactionReference(), formatAmount(b.getAmount()),
                        b.getRiskScore() != null ? b.getRiskScore() : 85,
                        b.getRiskLevel() != null ? b.getRiskLevel() : RiskLevel.HIGH,
                        b.getFraudProbability() != null ? b.getFraudProbability() : (b.getRiskScore() != null ? (double) b.getRiskScore() : 85.0),
                        b.getRiskReasons() != null ? b.getRiskReasons() : "Step-up verification challenge was not completed."
                );
                return buildResponse(ans, "BLOCKED_REASON", 0.98,
                        List.of("Can I afford a ₹10,000 transfer?", "What is my financial health score?", "Show my last 5 transactions"),
                        "/transfer");
            } else {
                return buildResponse("Great news! You have no blocked or declined transactions on record. All your transactions are in good standing.",
                        "BLOCKED_REASON", 0.92,
                        List.of("How much did I spend this month?", "What is my financial health score?"),
                        "/dashboard");
            }
        }

        // 3. Affordability Check Intent ("Can I afford a ₹20,000 transfer?")
        if (containsAny(lower, "afford", "can i send", "can i spend", "can i buy", "can i transfer")) {
            BigDecimal targetAmount = null;
            Matcher m = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)").matcher(lower);
            while (m.find()) {
                try {
                    String clean = m.group(1).replace(",", "");
                    BigDecimal val = new BigDecimal(clean);
                    if (val.compareTo(BigDecimal.ZERO) > 0) {
                        targetAmount = val;
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (targetAmount == null) {
                targetAmount = BigDecimal.valueOf(10000);
            }

            BigDecimal remaining = totalBalance.subtract(targetAmount);
            if (remaining.compareTo(BigDecimal.ZERO) >= 0) {
                double pct = totalBalance.compareTo(BigDecimal.ZERO) > 0
                        ? targetAmount.divide(totalBalance, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                        : 100.0;
                String safetyAdvice;
                if (pct <= 25.0) {
                    safetyAdvice = "✅ **Yes, comfortably.** This transfer represents only " + String.format("%.1f%%", pct) + " of your available balance, leaving a healthy safety cushion.";
                } else if (pct <= 60.0) {
                    safetyAdvice = "⚠️ **Yes, but with caution.** This will utilize " + String.format("%.1f%%", pct) + " of your total balance. You will have ₹" + formatAmount(remaining) + " remaining.";
                } else {
                    safetyAdvice = "🚨 **High balance impact.** This transfer requires " + String.format("%.1f%%", pct) + " of your entire liquid balance, leaving only ₹" + formatAmount(remaining) + ". Our AI engine may also flag this as a balance depletion anomaly.";
                }

                String ans = String.format(
                        "**Affordability Analysis for ₹%s:**\n\n" +
                        "• **Current Total Balance:** ₹%s\n" +
                        "• **Balance After Transfer:** ₹%s\n" +
                        "• **Evaluation:** %s",
                        formatAmount(targetAmount), formatAmount(totalBalance), formatAmount(remaining), safetyAdvice
                );
                return buildResponse(ans, "AFFORDABILITY_CHECK", 0.96,
                        List.of("How much did I spend this month?", "What is my financial health score?", "Show my last 5 transactions"),
                        "/transfer");
            } else {
                String ans = String.format(
                        "❌ **Insufficient Balance.** You cannot afford a transfer of **₹%s** right now.\n\n" +
                        "• **Available Balance:** ₹%s\n" +
                        "• **Shortfall:** ₹%s\n\n" +
                        "You need to deposit or receive at least ₹%s to proceed.",
                        formatAmount(targetAmount), formatAmount(totalBalance), formatAmount(targetAmount.subtract(totalBalance)), formatAmount(targetAmount.subtract(totalBalance))
                );
                return buildResponse(ans, "AFFORDABILITY_CHECK", 0.98,
                        List.of("Show my account balance", "How much did I spend this month?"),
                        "/accounts");
            }
        }

        // 4. Spending Intent ("How much did I spend this month?")
        if (containsAny(lower, "spend", "spending", "expenses", "spent", "outflow")) {
            FinancialHealthResponse fh = financialHealthService.calculateFinancialHealth(username);
            if (fh == null) {
                fh = FinancialHealthResponse.builder()
                        .currentMonthSpending(BigDecimal.ZERO)
                        .currentMonthIncome(BigDecimal.ZERO)
                        .currentMonthSavings(BigDecimal.ZERO)
                        .savingsRate(0.0)
                        .categoryDistribution(List.of())
                        .build();
            }
            String topCat = (fh.getCategoryDistribution() != null && !fh.getCategoryDistribution().isEmpty())
                    ? fh.getCategoryDistribution().get(0).getDisplayName() : "None";
            String topAmt = (fh.getCategoryDistribution() != null && !fh.getCategoryDistribution().isEmpty())
                    ? formatAmount(fh.getCategoryDistribution().get(0).getAmount()) : "0.00";

            String ans = String.format(
                    "You have spent **₹%s** so far this month.\n\n" +
                    "• **Top Category:** %s (₹%s)\n" +
                    "• **Month-over-Month Trend:** %s\n" +
                    "• **Projected Month-End:** ₹%s",
                    formatAmount(fh.getCurrentMonthSpending()),
                    topCat,
                    topAmt,
                    fh.getMonthOverMonthPercent() != null && fh.getMonthOverMonthPercent() > 0 ? "+" + fh.getMonthOverMonthPercent() + "% increase" : (fh.getMonthOverMonthPercent() != null ? fh.getMonthOverMonthPercent() + "% decrease" : "Steady"),
                    fh.getSpendingPrediction() != null && fh.getSpendingPrediction().getPredictedMonthEndSpending() != null ? formatAmount(fh.getSpendingPrediction().getPredictedMonthEndSpending()) : "N/A"
            );
            return buildResponse(ans, "SPENDING_SUMMARY", 0.95,
                    List.of("Which category do I spend the most on?", "How much did I save this month?", "Can I afford a ₹15,000 transfer?"),
                    "/financial-health");
        }

        // 5. Top Category Intent
        if (containsAny(lower, "category", "most on", "highest expense", "where does my money go")) {
            FinancialHealthResponse fh = financialHealthService.calculateFinancialHealth(username);
            if (fh != null && fh.getCategoryDistribution() != null && !fh.getCategoryDistribution().isEmpty()) {
                var top = fh.getCategoryDistribution().get(0);
                String ans = String.format(
                        "Your largest spending category this month is **%s** with an outflow of **₹%s** (%.1f%% of total monthly spending).\n\n" +
                        "Breakdown:\n",
                        top.getDisplayName(), formatAmount(top.getAmount()), top.getPercentage());
                for (int i = 0; i < Math.min(4, fh.getCategoryDistribution().size()); i++) {
                    var c = fh.getCategoryDistribution().get(i);
                    ans += String.format("• %s: ₹%s (%.1f%%)\n", c.getDisplayName(), formatAmount(c.getAmount()), c.getPercentage());
                }
                return buildResponse(ans, "TOP_CATEGORY", 0.94,
                        List.of("How much did I spend this month?", "Show my last 5 transactions", "What is my financial health score?"),
                        "/financial-health");
            } else {
                return buildResponse("You don't have any debit expenses categorized for this month yet.", "TOP_CATEGORY", 0.90, List.of(), "/financial-health");
            }
        }

        // 6. Savings Intent ("How much did I save this month?")
        if (containsAny(lower, "save", "savings", "saved", "saving rate")) {
            FinancialHealthResponse fh = financialHealthService.calculateFinancialHealth(username);
            if (fh == null) {
                fh = FinancialHealthResponse.builder()
                        .currentMonthSpending(BigDecimal.ZERO)
                        .currentMonthIncome(BigDecimal.ZERO)
                        .currentMonthSavings(BigDecimal.ZERO)
                        .savingsRate(0.0)
                        .savingsScore(75)
                        .categoryDistribution(List.of())
                        .build();
            }
            String ans = String.format(
                    "This month, your total net savings is **₹%s** with an effective savings rate of **%.1f%%**.\n\n" +
                    "• **Monthly Income Inflow:** ₹%s\n" +
                    "• **Monthly Debits / Expenses:** ₹%s\n" +
                    "• **Savings Health Score:** %d / 100",
                    formatAmount(fh.getCurrentMonthSavings()),
                    fh.getSavingsRate() != null ? fh.getSavingsRate() : 0.0,
                    formatAmount(fh.getCurrentMonthIncome()),
                    formatAmount(fh.getCurrentMonthSpending()),
                    fh.getSavingsScore() != null ? fh.getSavingsScore() : 75
            );
            return buildResponse(ans, "MONTHLY_SAVINGS", 0.95,
                    List.of("What is my financial health score?", "How much did I spend this month?", "Can I afford a ₹25,000 transfer?"),
                    "/financial-health");
        }

        // 7. Recent Transactions Intent ("Show my last 5 transactions")
        if (containsAny(lower, "transaction", "transfers", "last", "history", "recent", "past")) {
            int limit = 5;
            Matcher m = Pattern.compile("\\b(\\d+)\\b").matcher(lower);
            if (m.find()) {
                try { limit = Math.min(10, Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
            }

            List<Transaction> sorted = userTransactions.stream()
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .limit(limit)
                    .toList();

            if (sorted.isEmpty()) {
                return buildResponse("No recent transactions found on your account ledger.", "RECENT_TRANSACTIONS", 0.95, List.of(), "/transactions");
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Here are your last %d recorded transaction(s):\n\n", sorted.size()));
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

            for (Transaction tx : sorted) {
                String sign = tx.getType().name().equals("DEPOSIT") ? "+" : "-";
                sb.append(String.format("• **%s₹%s** — %s (`%s`) | %s [%s, Risk: %d/100]\n",
                        sign, formatAmount(tx.getAmount()), tx.getDescription(), tx.getType(),
                        tx.getTimestamp().format(dtf), tx.getStatus(), tx.getRiskScore() != null ? tx.getRiskScore() : 10));
            }
            return buildResponse(sb.toString(), "RECENT_TRANSACTIONS", 0.97,
                    List.of("Why was my transaction blocked?", "What is my financial health score?", "How much did I spend this month?"),
                    "/transactions");
        }

        // 8. Financial Health Score Intent
        if (containsAny(lower, "health", "score", "financial health", "wellness", "rating")) {
            FinancialHealthResponse fh = financialHealthService.calculateFinancialHealth(username);
            String ans = String.format(
                    "Your overall **Financial Health Score** is **%d / 100** (%s).\n\n" +
                    "• **Savings Score:** %d/100 (Savings Rate: %.1f%%)\n" +
                    "• **Spending Discipline Score:** %d/100\n" +
                    "• **Transaction Risk Score:** %d/100\n\n" +
                    "💡 *Insight: %s*",
                    fh.getFinancialHealthScore(), fh.getHealthGrade(),
                    fh.getSavingsScore(), fh.getSavingsRate(),
                    fh.getSpendingScore(),
                    fh.getTransactionRiskScore(),
                    !fh.getIntelligentInsights().isEmpty() ? fh.getIntelligentInsights().get(0) : "Your finances are monitored and healthy."
            );
            return buildResponse(ans, "FINANCIAL_HEALTH", 0.97,
                    List.of("How much did I spend this month?", "Can I afford a ₹20,000 transfer?", "Which category do I spend the most on?"));
        }

        // 9. Greeting / General Help
        if (containsAny(lower, "hello", "hi", "hey", "help", "who are you", "what can you do")) {
            String ans = String.format(
                    "Hello %s! I am **SmartBank AI Assistant**, your private, real-time banking intelligence companion.\n\n" +
                    "You can ask me questions such as:\n" +
                    "• *\"How much did I spend this month?\"*\n" +
                    "• *\"Show my last 5 transactions\"*\n" +
                    "• *\"Which category do I spend the most on?\"*\n" +
                    "• *\"How much did I save this month?\"*\n" +
                    "• *\"Why was my transaction blocked?\"*\n" +
                    "• *\"What is my financial health score?\"*\n" +
                    "• *\"Can I afford a ₹20,000 transfer?\"*\n\n" +
                    "All queries are processed privately using only your personal ledger.",
                    user.getFullName());
            return buildResponse(ans, "GREETING", 0.99,
                    List.of("How much did I spend this month?", "What is my financial health score?", "Can I afford a ₹10,000 transfer?"));
        }

        // Default Fallback
        return buildResponse(
                "I understood your request, but I need a little more context. You can ask about your **balance**, **monthly spending**, **savings**, **recent transactions**, **financial health score**, or **transfer affordability**.",
                "GENERAL_FALLBACK", 0.50,
                List.of("How much did I spend this month?", "Show my last 5 transactions", "What is my financial health score?", "Can I afford a ₹15,000 transfer?")
        );
    }

    private boolean containsAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }

    private AiAssistantResponse buildResponse(String answer, String intent, double confidence, List<String> suggestions) {
        return buildResponse(answer, intent, confidence, suggestions, null);
    }

    private AiAssistantResponse buildResponse(String answer, String intent, double confidence, List<String> suggestions, String actionUrl) {
        return AiAssistantResponse.builder()
                .answer(answer)
                .intentDetected(intent)
                .confidence(confidence)
                .suggestedQuestions(suggestions)
                .actionUrl(actionUrl)
                .timestamp(LocalDateTime.now())
                .engineUsed("SmartBank Local Intent Engine")
                .build();
    }

    private String formatAmount(BigDecimal amt) {
        if (amt == null) return "0.00";
        return String.format("%,.2f", amt);
    }
}
