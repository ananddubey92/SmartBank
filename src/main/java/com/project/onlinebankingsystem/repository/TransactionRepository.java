package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.*;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount = :account OR t.destinationAccount = :account ORDER BY t.timestamp DESC")
    List<Transaction> findAllByAccount(@Param("account") Account account);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount IN :accounts OR t.destinationAccount IN :accounts ORDER BY t.timestamp DESC")
    List<Transaction> findAllByAccounts(@Param("accounts") List<Account> accounts);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount IN :accounts OR t.destinationAccount IN :accounts) AND t.timestamp >= :since ORDER BY t.timestamp DESC")
    List<Transaction> findAllByAccountsSince(@Param("accounts") List<Account> accounts, @Param("since") LocalDateTime since);

    List<Transaction> findAllByOrderByTimestampDesc();

    List<Transaction> findByRiskLevel(RiskLevel riskLevel);

    List<Transaction> findByStatus(TransactionStatus status);

    long countByRiskLevel(RiskLevel riskLevel);

    long countByStatus(TransactionStatus status);

    Optional<Transaction> findByTransactionReference(String transactionReference);

    @Query("SELECT t FROM Transaction t WHERE t.riskLevel IN (:riskLevels) OR t.status IN (:statuses) ORDER BY t.timestamp DESC")
    List<Transaction> findSuspiciousTransactions(@Param("riskLevels") List<RiskLevel> riskLevels, @Param("statuses") List<TransactionStatus> statuses);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount = :account OR t.destinationAccount = :account) AND t.timestamp >= :startDate AND t.timestamp <= :endDate ORDER BY t.timestamp ASC, t.id ASC")
    List<Transaction> findStatementTransactions(@Param("account") Account account, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount = :account OR t.destinationAccount = :account) AND t.timestamp < :startDate ORDER BY t.timestamp ASC, t.id ASC")
    List<Transaction> findAllTransactionsBeforeDate(@Param("account") Account account, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount = :account OR t.destinationAccount = :account) AND t.timestamp >= :sinceDate ORDER BY t.timestamp ASC, t.id ASC")
    List<Transaction> findAllTransactionsFromDate(@Param("account") Account account, @Param("sinceDate") LocalDateTime sinceDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.sourceAccount = :account AND t.status = 'COMPLETED' AND t.timestamp >= :since")
    BigDecimal sumTransfersSince(@Param("account") Account account, @Param("since") LocalDateTime since);
}
