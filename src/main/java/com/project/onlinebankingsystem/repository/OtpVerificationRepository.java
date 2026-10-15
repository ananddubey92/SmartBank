package com.project.onlinebankingsystem.repository;

import com.project.onlinebankingsystem.model.OtpVerification;
import com.project.onlinebankingsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByTransactionReferenceAndVerifiedFalseOrderByCreatedAtDesc(String transactionReference);

    Optional<OtpVerification> findTopByUserAndVerifiedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(User user, LocalDateTime now);

    void deleteByExpiryTimeBefore(LocalDateTime cutoff);
}
