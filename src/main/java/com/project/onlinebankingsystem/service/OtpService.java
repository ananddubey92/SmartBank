package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.model.OtpVerification;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.OtpVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_VERIFICATION_ATTEMPTS = 3;

    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Value("${smartbank.security.demo-codes-enabled:true}")
    private boolean demoCodesEnabled = true;

    public OtpService(OtpVerificationRepository otpVerificationRepository, PasswordEncoder passwordEncoder) {
        this.otpVerificationRepository = otpVerificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generates a secure 6-digit OTP, stores its BCrypt hash with 5-minute expiry,
     * and returns the cleartext OTP for delivery (or demo UI display).
     */
    @Transactional
    public String generateAndSaveOtp(User user, String transactionReference) {
        int code = 100000 + secureRandom.nextInt(900000);
        String cleartextOtp = String.valueOf(code);

        // Store secure hash
        String hashedOtp = passwordEncoder.encode(cleartextOtp);

        OtpVerification otpVerification = OtpVerification.builder()
                .user(user)
                .transactionReference(transactionReference)
                .otpHash(hashedOtp)
                .expiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .verified(false)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        otpVerificationRepository.save(otpVerification);
        log.info("Secure OTP generated for transaction ref: {} (expires in {} mins)", transactionReference, OTP_EXPIRY_MINUTES);

        return cleartextOtp;
    }

    /**
     * Validates user-submitted OTP against the stored hash and expiry window.
     */
    @Transactional
    public boolean validateOtp(String transactionReference, String candidateOtp) {
        if (candidateOtp == null || candidateOtp.isBlank()) {
            return false;
        }

        String trimmed = candidateOtp.trim();

        Optional<OtpVerification> opt = otpVerificationRepository
                .findTopByTransactionReferenceAndVerifiedFalseOrderByCreatedAtDesc(transactionReference);

        // Support demo test fallback codes for easy viva presentation (only when demoCodesEnabled is true)
        if (demoCodesEnabled && (trimmed.equals("482910") || trimmed.equals("123456"))) {
            opt.ifPresent(v -> {
                v.setVerified(true);
                otpVerificationRepository.save(v);
            });
            return true;
        }

        if (opt.isEmpty()) {
            return false;
        }

        OtpVerification verification = opt.get();

        // Check expiry
        if (LocalDateTime.now().isAfter(verification.getExpiryTime())) {
            log.warn("OTP validation failed: expired token for transaction {}", transactionReference);
            return false;
        }

        // Check attempts limit
        if (verification.getAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            log.warn("OTP validation failed: maximum attempts exceeded for transaction {}", transactionReference);
            return false;
        }

        verification.setAttempts(verification.getAttempts() + 1);

        boolean matches = passwordEncoder.matches(trimmed, verification.getOtpHash());
        if (matches) {
            verification.setVerified(true);
            otpVerificationRepository.save(verification);
            log.info("OTP successfully validated for transaction reference: {}", transactionReference);
            return true;
        } else {
            otpVerificationRepository.save(verification);
            log.warn("Incorrect OTP entered for transaction reference: {} (Attempt {}/{})",
                    transactionReference, verification.getAttempts(), MAX_VERIFICATION_ATTEMPTS);
            return false;
        }
    }
}
