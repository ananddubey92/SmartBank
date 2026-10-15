package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.ExpenseAnalyticsResponse;
import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseAnalyticsService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public ExpenseAnalyticsService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Categorize a description automatically using NLP-style keyword matching.
     */
    public ExpenseCategory autoDetectCategory(String text) {
        if (text == null || text.isBlank()) {
            return ExpenseCategory.OTHER;
        }
        String lower = text.toLowerCase().trim();

        if (matchesAny(lower, "food", "restaurant", "cafe", "dinner", "lunch", "breakfast", "zomato", "swiggy", "groceries", "supermarket", "pizza", "burger", "meal", "coffee", "bakery", "mcdonald", "kfc", "domino", "starbucks", "bar")) {
            return ExpenseCategory.FOOD;
        }
        if (matchesAny(lower, "shopping", "amazon", "flipkart", "myntra", "store", "cloth", "fashion", "mall", "purchase", "apparel", "shoes", "electronics", "zara", "h&m", "retail")) {
            return ExpenseCategory.SHOPPING;
        }
        if (matchesAny(lower, "bill", "electricity", "water", "rent", "recharge", "mobile", "wifi", "broadband", "power", "gas", "utility", "dth", "maintenance", "internet", "airtel", "jio")) {
            return ExpenseCategory.BILLS;
        }
        if (matchesAny(lower, "travel", "uber", "ola", "flight", "air", "train", "irctc", "metro", "bus", "hotel", "trip", "fuel", "petrol", "diesel", "toll", "cab", "makemytrip", "goibibo", "booking")) {
            return ExpenseCategory.TRAVEL;
        }
        if (matchesAny(lower, "education", "school", "college", "tuition", "course", "fees", "udemy", "coursera", "book", "exam", "university", "academy", "training", "degree")) {
            return ExpenseCategory.EDUCATION;
        }
        if (matchesAny(lower, "entertainment", "movie", "cinema", "netflix", "spotify", "prime", "hotstar", "concert", "game", "theatre", "ticket", "disney", "youtube", "music", "show")) {
            return ExpenseCategory.ENTERTAINMENT;
        }
        if (matchesAny(lower, "health", "hospital", "clinic", "pharmacy", "medicine", "doctor", "lab", "dental", "dental care", "medical", "apollo", "treatment", "cure", "drug", "diagnostic")) {
            return ExpenseCategory.HEALTHCARE;
        }

        return ExpenseCategory.OTHER;
    }

    private boolean matchesAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Compute comprehensive expense analytics & dynamic insights for a user.
     */
    public ExpenseAnalyticsResponse getAnalyticsForUser(String username) {
        List<Account> accounts = accountRepository.findByUserUsername(username);
        if (accounts.isEmpty()) {
            return buildEmptyResponse();
        }

        Set<String> userAccountNumbers = accounts.stream()
                .map(Account::getAccountNumber)
                .collect(Collectors.toSet());

        List<Transaction> allTransactions = transactionRepository.findAllByAccounts(accounts);

        LocalDate now = LocalDate.now();
        LocalDate startOfCurrentMonth = now.withDayOfMonth(1);
        LocalDate startOfPreviousMonth = startOfCurrentMonth.minusMonths(1);
        LocalDate endOfPreviousMonth = startOfCurrentMonth.minusDays(1);

        BigDecimal currentMonthSpending = BigDecimal.ZERO;
        BigDecimal previousMonthSpending = BigDecimal.ZERO;

        Map<ExpenseCategory, BigDecimal> currentMonthCategoryTotals = new EnumMap<>(ExpenseCategory.class);
        Map<ExpenseCategory, BigDecimal> previousMonthCategoryTotals = new EnumMap<>(ExpenseCategory.class);

        for (ExpenseCategory cat : ExpenseCategory.values()) {
            currentMonthCategoryTotals.put(cat, BigDecimal.ZERO);
            previousMonthCategoryTotals.put(cat, BigDecimal.ZERO);
        }

        BigDecimal totalTransferred = BigDecimal.ZERO;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;

        long successCount = 0;
        long failedCount = 0;
        long blockedCount = 0;
        long lowRiskCount = 0;
        long mediumRiskCount = 0;
        long highRiskCount = 0;

        for (Transaction tx : allTransactions) {
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;

            // Status counts
            if (tx.getStatus() == TransactionStatus.SUCCESS) {
                successCount++;
            } else if (tx.getStatus() == TransactionStatus.BLOCKED) {
                blockedCount++;
            } else {
                failedCount++;
            }

            // Risk Level counts
            RiskLevel rl = tx.getRiskLevel() != null ? tx.getRiskLevel() : RiskLevel.LOW;
            if (rl == RiskLevel.LOW) lowRiskCount++;
            else if (rl == RiskLevel.MEDIUM) mediumRiskCount++;
            else if (rl == RiskLevel.HIGH) highRiskCount++;

            // Inflow vs Outflow
            boolean isDebit = false;
            if (tx.getType() == TransactionType.WITHDRAWAL) {
                isDebit = true;
                totalWithdrawals = totalWithdrawals.add(amt);
            } else if (tx.getType() == TransactionType.TRANSFER) {
                if (tx.getSourceAccount() != null && userAccountNumbers.contains(tx.getSourceAccount().getAccountNumber())) {
                    isDebit = true;
                    totalTransferred = totalTransferred.add(amt);
                } else if (tx.getDestinationAccount() != null && userAccountNumbers.contains(tx.getDestinationAccount().getAccountNumber())) {
                    totalDeposits = totalDeposits.add(amt);
                }
            } else if (tx.getType() == TransactionType.DEPOSIT) {
                totalDeposits = totalDeposits.add(amt);
            }

            // Outflow expense categorization
            if (isDebit && tx.getStatus() == TransactionStatus.SUCCESS) {
                ExpenseCategory category = tx.getCategory() != null ? tx.getCategory() : autoDetectCategory(tx.getDescription());
                LocalDate txDate = tx.getTimestamp().toLocalDate();

                if (!txDate.isBefore(startOfCurrentMonth)) {
                    currentMonthSpending = currentMonthSpending.add(amt);
                    currentMonthCategoryTotals.put(category, currentMonthCategoryTotals.get(category).add(amt));
                } else if (!txDate.isBefore(startOfPreviousMonth) && !txDate.isAfter(endOfPreviousMonth)) {
                    previousMonthSpending = previousMonthSpending.add(amt);
                    previousMonthCategoryTotals.put(category, previousMonthCategoryTotals.get(category).add(amt));
                }
            }
        }

        // Calculate differences
        BigDecimal diff = currentMonthSpending.subtract(previousMonthSpending);
        Double diffPercent = 0.0;
        if (previousMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
            diffPercent = diff.divide(previousMonthSpending, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        // Category breakdown list
        List<ExpenseAnalyticsResponse.CategoryItem> categoryList = new ArrayList<>();
        ExpenseCategory highestCat = ExpenseCategory.OTHER;
        BigDecimal highestAmt = BigDecimal.ZERO;

        for (ExpenseCategory cat : ExpenseCategory.values()) {
            BigDecimal catAmt = currentMonthCategoryTotals.get(cat);
            if (catAmt.compareTo(highestAmt) > 0) {
                highestAmt = catAmt;
                highestCat = cat;
            }
            double pct = 0.0;
            if (currentMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
                pct = catAmt.divide(currentMonthSpending, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).doubleValue();
            }
            categoryList.add(ExpenseAnalyticsResponse.CategoryItem.builder()
                    .category(cat.name())
                    .displayName(cat.getDisplayName())
                    .icon(cat.getIcon())
                    .color(cat.getColor())
                    .amount(catAmt)
                    .percentage(pct)
                    .build());
        }

        // Generate Dynamic Spending Insights from real transaction data
        List<String> insights = new ArrayList<>();
        if (currentMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
            insights.add(String.format("Your current monthly spending is ₹%s.", formatCurrency(currentMonthSpending)));
            if (highestAmt.compareTo(BigDecimal.ZERO) > 0) {
                insights.add(String.format("Your highest spending category is %s at ₹%s (%s%% of total expenses).",
                        highestCat.getDisplayName(), formatCurrency(highestAmt),
                        currentMonthSpending.compareTo(BigDecimal.ZERO) > 0
                                ? highestAmt.divide(currentMonthSpending, 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).intValue()
                                : 0));
            }
            if (diff.compareTo(BigDecimal.ZERO) > 0 && previousMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
                insights.add(String.format("You spent ₹%s more than last month (+%.1f%%).", formatCurrency(diff), Math.abs(diffPercent)));
            } else if (diff.compareTo(BigDecimal.ZERO) < 0 && previousMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
                insights.add(String.format("Great job! You saved ₹%s compared to last month (-%.1f%%).", formatCurrency(diff.abs()), Math.abs(diffPercent)));
            }

            // Category specific comparison
            for (ExpenseCategory cat : ExpenseCategory.values()) {
                BigDecimal curCat = currentMonthCategoryTotals.get(cat);
                BigDecimal prevCat = previousMonthCategoryTotals.get(cat);
                if (curCat.compareTo(prevCat) > 0 && prevCat.compareTo(BigDecimal.ZERO) > 0) {
                    insights.add(String.format("Your %s expenses increased this month compared to previous cycle.", cat.getDisplayName()));
                    break;
                }
            }
        } else {
            insights.add("No debit spending recorded for the current month yet.");
            insights.add("SmartBank AI will monitor and categorize all your upcoming transactions.");
        }

        // 6-Month Inflow vs Outflow Trend
        List<String> trendMonths = new ArrayList<>();
        List<BigDecimal> inflowTrends = new ArrayList<>();
        List<BigDecimal> outflowTrends = new ArrayList<>();
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");

        for (int i = 5; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            LocalDate mStart = m.withDayOfMonth(1);
            LocalDate mEnd = m.withDayOfMonth(m.lengthOfMonth());
            trendMonths.add(m.format(monthFmt));

            BigDecimal mInflow = BigDecimal.ZERO;
            BigDecimal mOutflow = BigDecimal.ZERO;

            for (Transaction tx : allTransactions) {
                if (tx.getStatus() != TransactionStatus.SUCCESS) continue;
                LocalDate d = tx.getTimestamp().toLocalDate();
                if (!d.isBefore(mStart) && !d.isAfter(mEnd)) {
                    BigDecimal a = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                    if (tx.getType() == TransactionType.DEPOSIT) {
                        mInflow = mInflow.add(a);
                    } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                        mOutflow = mOutflow.add(a);
                    } else if (tx.getType() == TransactionType.TRANSFER) {
                        if (tx.getSourceAccount() != null && userAccountNumbers.contains(tx.getSourceAccount().getAccountNumber())) {
                            mOutflow = mOutflow.add(a);
                        }
                        if (tx.getDestinationAccount() != null && userAccountNumbers.contains(tx.getDestinationAccount().getAccountNumber())) {
                            mInflow = mInflow.add(a);
                        }
                    }
                }
            }
            inflowTrends.add(mInflow);
            outflowTrends.add(mOutflow);
        }

        return ExpenseAnalyticsResponse.builder()
                .currentMonthSpending(currentMonthSpending)
                .previousMonthSpending(previousMonthSpending)
                .monthOverMonthDiff(diff)
                .monthOverMonthPercent(diffPercent)
                .highestCategoryName(highestCat.getDisplayName())
                .highestCategoryAmount(highestAmt)
                .categoryBreakdown(categoryList)
                .insights(insights)
                .trendMonths(trendMonths)
                .inflowTrends(inflowTrends)
                .outflowTrends(outflowTrends)
                .totalTransactions(allTransactions.size())
                .successfulTransactions(successCount)
                .failedTransactions(failedCount)
                .blockedTransactions(blockedCount)
                .totalTransferred(totalTransferred)
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .lowRiskCount(lowRiskCount)
                .mediumRiskCount(mediumRiskCount)
                .highRiskCount(highRiskCount)
                .build();
    }

    private ExpenseAnalyticsResponse buildEmptyResponse() {
        List<ExpenseAnalyticsResponse.CategoryItem> emptyCategories = new ArrayList<>();
        for (ExpenseCategory cat : ExpenseCategory.values()) {
            emptyCategories.add(ExpenseAnalyticsResponse.CategoryItem.builder()
                    .category(cat.name())
                    .displayName(cat.getDisplayName())
                    .icon(cat.getIcon())
                    .color(cat.getColor())
                    .amount(BigDecimal.ZERO)
                    .percentage(0.0)
                    .build());
        }

        List<String> months = new ArrayList<>();
        List<BigDecimal> zeroes = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            months.add(now.minusMonths(i).getMonth().name().substring(0, 3));
            zeroes.add(BigDecimal.ZERO);
        }

        return ExpenseAnalyticsResponse.builder()
                .currentMonthSpending(BigDecimal.ZERO)
                .previousMonthSpending(BigDecimal.ZERO)
                .monthOverMonthDiff(BigDecimal.ZERO)
                .monthOverMonthPercent(0.0)
                .highestCategoryName("None")
                .highestCategoryAmount(BigDecimal.ZERO)
                .categoryBreakdown(emptyCategories)
                .insights(List.of("Perform your first transaction to generate AI budget optimizations and spending trends."))
                .trendMonths(months)
                .inflowTrends(zeroes)
                .outflowTrends(new ArrayList<>(zeroes))
                .totalTransactions(0)
                .successfulTransactions(0)
                .failedTransactions(0)
                .blockedTransactions(0)
                .totalTransferred(BigDecimal.ZERO)
                .totalDeposits(BigDecimal.ZERO)
                .totalWithdrawals(BigDecimal.ZERO)
                .lowRiskCount(0)
                .mediumRiskCount(0)
                .highRiskCount(0)
                .build();
    }

    private String formatCurrency(BigDecimal amt) {
        if (amt == null) return "0.00";
        return String.format("%,.2f", amt);
    }
}
