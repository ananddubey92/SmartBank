package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.UpiTransactionStatus;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.model.UpiTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UpiTransactionRepository extends JpaRepository<UpiTransaction, Long> {

    Optional<UpiTransaction> findByUpiReferenceId(String upiReferenceId);

    Optional<UpiTransaction> findByTransactionReference(String transactionReference);

    @Query("SELECT t FROM UpiTransaction t WHERE t.senderUser = :user OR t.receiverUser = :user ORDER BY t.timestamp DESC")
    List<UpiTransaction> findAllByUser(@Param("user") User user);

    List<UpiTransaction> findBySenderUserOrderByTimestampDesc(User senderUser);

    List<UpiTransaction> findByReceiverUserOrderByTimestampDesc(User receiverUser);

    List<UpiTransaction> findAllByOrderByTimestampDesc();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM UpiTransaction t WHERE t.senderUser = :user AND t.status = :status AND t.timestamp >= :since")
    BigDecimal calculateDailySpentAmount(@Param("user") User user,
                                         @Param("status") UpiTransactionStatus status,
                                         @Param("since") LocalDateTime since);

    List<UpiTransaction> findByRiskLevel(RiskLevel riskLevel);

    long countByStatus(UpiTransactionStatus status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM UpiTransaction t WHERE t.status = :status")
    BigDecimal calculateTotalVolumeByStatus(@Param("status") UpiTransactionStatus status);
}
