package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionReference(String transactionReference);

    List<LedgerEntry> findByAccountOrderByTimestampDesc(Account account);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.account = :account AND l.entryType = 'CREDIT'")
    BigDecimal sumCreditsForAccount(@Param("account") Account account);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.account = :account AND l.entryType = 'DEBIT'")
    BigDecimal sumDebitsForAccount(@Param("account") Account account);
}
