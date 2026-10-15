package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.FinancialHealthResponse;
import com.project.onlinebankingsystem.dto.FinancialHealthResponse.CategoryDistributionItem;
import com.project.onlinebankingsystem.dto.SpendingPredictionResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinancialHealthService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public FinancialHealthService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public FinancialHealthResponse calculateFinancialHealth(String username) {
        List<Account> accounts = accountRepository.findByUserUsername(username);
        if (accounts.isEmpty()) {
            return buildEmptyHealthResponse();
        }

        Set<String> userAccountNumbers = accounts.stream()
                .map(Account::getAccountNumber)
                .collect(Collectors.toSet());

        List<Transaction> transactions = transactionRepository.findAllByAccounts(accounts);

        LocalDate now = LocalDate.now();
        LocalDate startOfCurrentMonth = now.withDayOfMonth(1);
        LocalDate startOfPrevMonth = startOfCurrentMonth.minusMonths(1);
        LocalDate endOfPrevMonth = startOfCurrentMonth.minusDays(1);

        BigDecimal curMonthIncome = BigDecimal.ZERO;
        BigDecimal curMonthSpending = BigDecimal.ZERO;
        BigDecimal prevMonthSpending = BigDecimal.ZERO;
        BigDecimal prevMonthIncome = BigDecimal.ZERO;

        Map<ExpenseCategory, BigDecimal> categoryTotals = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) {
            categoryTotals.put(c, BigDecimal.ZERO);
        }

        int suspiciousCount = 0;
        int totalEvaluated = 0;

        for (Transaction tx : transactions) {
            if (tx.getStatus() != TransactionStatus.SUCCESS && tx.getStatus() != TransactionStatus.COMPLETED
                    && tx.getStatus() != TransactionStatus.BLOCKED) {
                continue;
            }

            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            LocalDate txDate = tx.getTimestamp().toLocalDate();

            totalEvaluated++;
            if (tx.getRiskLevel() == RiskLevel.HIGH || tx.getRiskLevel() == RiskLevel.CRITICAL
                    || tx.getStatus() == TransactionStatus.BLOCKED) {
                suspiciousCount++;
            }

            boolean isDebit = false;
            boolean isCredit = false;

            if (tx.getType() == TransactionType.DEPOSIT) {
                isCredit = true;
            } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                isDebit = true;
            } else if (tx.getType() == TransactionType.TRANSFER) {
                if (tx.getSourceAccount() != null && userAccountNumbers.contains(tx.getSourceAccount().getAccountNumber())) {
                    isDebit = true;
                }
                if (tx.getDestinationAccount() != null && userAccountNumbers.contains(tx.getDestinationAccount().getAccountNumber())) {
                    isCredit = true;
                }
            }

            // Current Month Aggregations
            if (!txDate.isBefore(startOfCurrentMonth)) {
                if (isCredit) {
                    curMonthIncome = curMonthIncome.add(amt);
                }
                if (isDebit) {
                    curMonthSpending = curMonthSpending.add(amt);
                    ExpenseCategory cat = tx.getCategory() != null ? tx.getCategory() : ExpenseCategory.OTHER;
                    categoryTotals.put(cat, categoryTotals.get(cat).add(amt));
                }
            } else if (!txDate.isBefore(startOfPrevMonth) && !txDate.isAfter(endOfPrevMonth)) {
                // Previous Month Aggregations
                if (isCredit) {
                    prevMonthIncome = prevMonthIncome.add(amt);
                }
                if (isDebit) {
                    prevMonthSpending = prevMonthSpending.add(amt);
                }
            }
        }

        // Monthly Savings
        BigDecimal curMonthSavings = curMonthIncome.subtract(curMonthSpending);
        if (curMonthSavings.compareTo(BigDecimal.ZERO) < 0) {
            curMonthSavings = BigDecimal.ZERO;
        }

        // Savings Rate %
        double savingsRate = 0.0;
        if (curMonthIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = curMonthSavings.divide(curMonthIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        // Month-over-Month Spending Change
        BigDecimal momChange = curMonthSpending.subtract(prevMonthSpending);
        Double momPercent = 0.0;
        if (prevMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
            momPercent = momChange.divide(prevMonthSpending, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        // 1. Savings Score (0–100): 20% savings rate = 80 score, 35%+ = 100
        int savingsScore = (int) Math.min(100, Math.max(15, Math.round(savingsRate * 2.8)));

        // 2. Spending Score (0–100): based on ratio of spending to income
        int spendingScore = 80;
        if (curMonthIncome.compareTo(BigDecimal.ZERO) > 0) {
            double spendRatio = curMonthSpending.divide(curMonthIncome, 4, RoundingMode.HALF_UP).doubleValue();
            if (spendRatio <= 0.50) spendingScore = 95;
            else if (spendRatio <= 0.70) spendingScore = 85;
            else if (spendRatio <= 0.85) spendingScore = 70;
            else spendingScore = 45;
        }

        // 3. Transaction Risk Score (0–100): penalizes fraud blocks and high risk
        int transactionRiskScore = Math.max(20, 100 - (suspiciousCount * 18));

        // Overall Financial Health Score:
        // Weighted: 35% Savings + 35% Spending + 30% Risk
        int overallHealthScore = (int) Math.round((savingsScore * 0.35) + (spendingScore * 0.35) + (transactionRiskScore * 0.30));
        overallHealthScore = Math.min(100, Math.max(10, overallHealthScore));

        String grade = "MODERATE";
        if (overallHealthScore >= 80) grade = "EXCELLENT";
        else if (overallHealthScore >= 65) grade = "GOOD";
        else if (overallHealthScore < 45) grade = "NEEDS_ATTENTION";

        // 6-Month Trend Data
        List<String> trendMonths = new ArrayList<>();
        List<BigDecimal> monthlyIncomeTrend = new ArrayList<>();
        List<BigDecimal> monthlyExpenseTrend = new ArrayList<>();
        List<BigDecimal> monthlySavingsTrend = new ArrayList<>();
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");

        for (int i = 5; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            LocalDate mStart = m.withDayOfMonth(1);
            LocalDate mEnd = m.withDayOfMonth(m.lengthOfMonth());
            trendMonths.add(m.format(monthFmt));

            BigDecimal mIn = BigDecimal.ZERO;
            BigDecimal mOut = BigDecimal.ZERO;

            for (Transaction tx : transactions) {
                if (tx.getStatus() != TransactionStatus.SUCCESS && tx.getStatus() != TransactionStatus.COMPLETED) continue;
                LocalDate d = tx.getTimestamp().toLocalDate();
                if (!d.isBefore(mStart) && !d.isAfter(mEnd)) {
                    BigDecimal a = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                    if (tx.getType() == TransactionType.DEPOSIT) {
                        mIn = mIn.add(a);
                    } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                        mOut = mOut.add(a);
                    } else if (tx.getType() == TransactionType.TRANSFER) {
                        if (tx.getSourceAccount() != null && userAccountNumbers.contains(tx.getSourceAccount().getAccountNumber())) {
                            mOut = mOut.add(a);
                        }
                        if (tx.getDestinationAccount() != null && userAccountNumbers.contains(tx.getDestinationAccount().getAccountNumber())) {
                            mIn = mIn.add(a);
                        }
                    }
                }
            }
            monthlyIncomeTrend.add(mIn);
            monthlyExpenseTrend.add(mOut);
            BigDecimal mSav = mIn.subtract(mOut);
            monthlySavingsTrend.add(mSav.compareTo(BigDecimal.ZERO) > 0 ? mSav : BigDecimal.ZERO);
        }

        // Category Breakdown List
        List<CategoryDistributionItem> catDistribution = new ArrayList<>();
        ExpenseCategory topCat = ExpenseCategory.OTHER;
        BigDecimal topCatAmt = BigDecimal.ZERO;

        for (ExpenseCategory c : ExpenseCategory.values()) {
            BigDecimal amt = categoryTotals.get(c);
            if (amt.compareTo(topCatAmt) > 0) {
                topCatAmt = amt;
                topCat = c;
            }
            double pct = 0.0;
            if (curMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
                pct = amt.divide(curMonthSpending, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).doubleValue();
            }
            catDistribution.add(CategoryDistributionItem.builder()
                    .category(c.name())
                    .displayName(c.getDisplayName())
                    .color(c.getColor())
                    .icon(c.getIcon())
                    .amount(amt)
                    .percentage(pct)
                    .build());
        }

        // Generate Intelligent Insights
        List<String> insights = new ArrayList<>();
        if (topCatAmt.compareTo(BigDecimal.ZERO) > 0) {
            insights.add(String.format("Your largest expense category this month is %s at ₹%s.",
                    topCat.getDisplayName(), formatCurrency(topCatAmt)));
        }
        if (momPercent > 0) {
            insights.add(String.format("You spent %.1f%% more this month compared to last month.", momPercent));
        } else if (momPercent < 0) {
            insights.add(String.format("Great discipline! Your spending decreased by %.1f%% compared to last month.", Math.abs(momPercent)));
        }
        if (savingsRate >= 20.0) {
            insights.add(String.format("Your current savings rate is %.1f%%, outperforming the recommended 20%% benchmark.", savingsRate));
        } else if (curMonthIncome.compareTo(BigDecimal.ZERO) > 0) {
            insights.add(String.format("Your savings rate is %.1f%%. Consider allocating an extra 5%% towards savings.", savingsRate));
        }
        if (suspiciousCount > 0) {
            insights.add(String.format("Security alert: %d suspicious or high-risk transfers were audited on your account.", suspiciousCount));
        } else {
            insights.add("Transaction risk profile is clean with zero recent security incidents.");
        }

        // Spending Prediction
        SpendingPredictionResponse prediction = calculateSpendingPrediction(now, curMonthSpending, prevMonthSpending, trendMonths, monthlyExpenseTrend);

        return FinancialHealthResponse.builder()
                .financialHealthScore(overallHealthScore)
                .savingsScore(savingsScore)
                .spendingScore(spendingScore)
                .transactionRiskScore(transactionRiskScore)
                .healthGrade(grade)
                .currentMonthIncome(curMonthIncome)
                .currentMonthSpending(curMonthSpending)
                .currentMonthSavings(curMonthSavings)
                .savingsRate(round(savingsRate, 1))
                .previousMonthSpending(prevMonthSpending)
                .monthOverMonthSpendingChange(momChange)
                .monthOverMonthPercent(momPercent)
                .trendMonths(trendMonths)
                .monthlyIncomeTrend(monthlyIncomeTrend)
                .monthlyExpenseTrend(monthlyExpenseTrend)
                .monthlySavingsTrend(monthlySavingsTrend)
                .categoryDistribution(catDistribution)
                .intelligentInsights(insights)
                .spendingPrediction(prediction)
                .build();
    }

    public SpendingPredictionResponse calculateSpendingPrediction(LocalDate now,
                                                                  BigDecimal curMonthSpending,
                                                                  BigDecimal prevMonthSpending,
                                                                  List<String> trendMonths,
                                                                  List<BigDecimal> monthlyExpenses) {
        int dayOfMonth = now.getDayOfMonth();
        int daysInMonth = now.lengthOfMonth();

        boolean hasHistory = prevMonthSpending.compareTo(BigDecimal.ZERO) > 0 || curMonthSpending.compareTo(BigDecimal.ZERO) > 0;
        if (!hasHistory) {
            return SpendingPredictionResponse.builder()
                    .sufficientData(false)
                    .dataStatusMessage("Insufficient transaction history to generate high-confidence spending predictions.")
                    .currentMonthSpending(BigDecimal.ZERO)
                    .dailyAverageSpending(BigDecimal.ZERO)
                    .predictedMonthEndSpending(BigDecimal.ZERO)
                    .predictedNextMonthSpending(BigDecimal.ZERO)
                    .confidenceScore(0.0)
                    .comparisonLabels(List.of())
                    .actualSpendingPoints(List.of())
                    .predictedSpendingPoints(List.of())
                    .build();
        }

        // Projected Month-End Spending = (currentSpending / daysElapsed) * totalDays
        BigDecimal runRateDaily = curMonthSpending.divide(BigDecimal.valueOf(Math.max(dayOfMonth, 1)), 2, RoundingMode.HALF_UP);
        BigDecimal predictedMonthEnd = runRateDaily.multiply(BigDecimal.valueOf(daysInMonth)).setScale(2, RoundingMode.HALF_UP);

        // Projected Next Month Spending = (0.6 * currentMonthProjected) + (0.4 * prevMonth)
        BigDecimal predictedNextMonth;
        if (prevMonthSpending.compareTo(BigDecimal.ZERO) > 0) {
            predictedNextMonth = predictedMonthEnd.multiply(BigDecimal.valueOf(0.60))
                    .add(prevMonthSpending.multiply(BigDecimal.valueOf(0.40)))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            predictedNextMonth = predictedMonthEnd.multiply(BigDecimal.valueOf(0.95)).setScale(2, RoundingMode.HALF_UP);
        }

        List<String> labels = List.of("Last Month", "Current (Actual)", "Month-End (Projected)", "Next Month (Forecast)");
        List<BigDecimal> actualPoints = Arrays.asList(prevMonthSpending, curMonthSpending, null, null);
        List<BigDecimal> predictedPoints = Arrays.asList(prevMonthSpending, curMonthSpending, predictedMonthEnd, predictedNextMonth);

        return SpendingPredictionResponse.builder()
                .sufficientData(true)
                .dataStatusMessage("Forecast modeled from historical daily run-rate and month-over-month moving average.")
                .currentMonthSpending(curMonthSpending)
                .dailyAverageSpending(runRateDaily)
                .predictedMonthEndSpending(predictedMonthEnd)
                .predictedNextMonthSpending(predictedNextMonth)
                .confidenceScore(87.5)
                .primaryDriver("Recurring lifestyle and essential utility outflows")
                .comparisonLabels(labels)
                .actualSpendingPoints(actualPoints)
                .predictedSpendingPoints(predictedPoints)
                .build();
    }

    public SpendingPredictionResponse predictSpending(String username) {
        FinancialHealthResponse health = calculateFinancialHealth(username);
        return health != null ? health.getSpendingPrediction() : null;
    }

    private FinancialHealthResponse buildEmptyHealthResponse() {
        return FinancialHealthResponse.builder()
                .financialHealthScore(65)
                .savingsScore(60)
                .spendingScore(70)
                .transactionRiskScore(90)
                .healthGrade("GOOD")
                .currentMonthIncome(BigDecimal.ZERO)
                .currentMonthSpending(BigDecimal.ZERO)
                .currentMonthSavings(BigDecimal.ZERO)
                .savingsRate(0.0)
                .previousMonthSpending(BigDecimal.ZERO)
                .monthOverMonthSpendingChange(BigDecimal.ZERO)
                .monthOverMonthPercent(0.0)
                .trendMonths(List.of("M1", "M2", "M3", "M4", "M5", "Current"))
                .monthlyIncomeTrend(List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .monthlyExpenseTrend(List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .monthlySavingsTrend(List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .categoryDistribution(List.of())
                .intelligentInsights(List.of("Perform your first transaction to unlock dynamic AI financial insights."))
                .spendingPrediction(SpendingPredictionResponse.builder()
                        .sufficientData(false)
                        .dataStatusMessage("Insufficient transaction records to build prediction series.")
                        .dailyAverageSpending(BigDecimal.ZERO)
                        .build())
                .build();
    }

    private double round(double val, int places) {
        return BigDecimal.valueOf(val).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatCurrency(BigDecimal amt) {
        if (amt == null) return "0.00";
        return String.format("%,.2f", amt);
    }
}
