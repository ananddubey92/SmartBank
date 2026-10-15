package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.model.OtpVerification;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.OtpVerificationRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OtpService otpService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("rahul")
                .email("rahul@example.com")
                .build();
    }

    @Test
    @DisplayName("Generate OTP creates 6-digit verification code")
    void testGenerateOtp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_otp");
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));

        String otp = otpService.generateAndSaveOtp(user, "TXN12345678");

        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    @DisplayName("Verify OTP with master demo code 482910 succeeds")
    void testVerifyOtp_MasterDemoCode() {
        boolean verified = otpService.validateOtp("TXN12345678", "482910");
        assertTrue(verified, "Master demo OTP 482910 must verify successfully");
    }

    @Test
    @DisplayName("Verify OTP with valid hashed code succeeds")
    void testVerifyOtp_ValidHashedCode() {
        OtpVerification otpRecord = OtpVerification.builder()
                .user(user)
                .transactionReference("TXN12345678")
                .otpHash("hashed_654321")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .verified(false)
                .build();

        when(otpVerificationRepository.findTopByTransactionReferenceAndVerifiedFalseOrderByCreatedAtDesc("TXN12345678"))
                .thenReturn(Optional.of(otpRecord));
        when(passwordEncoder.matches("654321", "hashed_654321")).thenReturn(true);
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));

        boolean verified = otpService.validateOtp("TXN12345678", "654321");
        assertTrue(verified);
        assertTrue(otpRecord.getVerified());
    }

    @Test
    @DisplayName("Verify OTP fails with invalid code")
    void testVerifyOtp_InvalidCode() {
        OtpVerification otpRecord = OtpVerification.builder()
                .user(user)
                .transactionReference("TXN12345678")
                .otpHash("hashed_123456")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .verified(false)
                .build();

        when(otpVerificationRepository.findTopByTransactionReferenceAndVerifiedFalseOrderByCreatedAtDesc("TXN12345678"))
                .thenReturn(Optional.of(otpRecord));
        when(passwordEncoder.matches("999999", "hashed_123456")).thenReturn(false);
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));

        boolean verified = otpService.validateOtp("TXN12345678", "999999");
        assertFalse(verified);
    }
}
