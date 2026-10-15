package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.RegisterRequest;
import com.project.onlinebankingsystem.dto.RegisterResponse;
import com.project.onlinebankingsystem.dto.ProfileUpdateRequest;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import com.project.onlinebankingsystem.enums.Gender;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import com.project.onlinebankingsystem.model.LoginActivity;
import com.project.onlinebankingsystem.repository.LoginActivityRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final LoginActivityRepository loginActivityRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       LoginActivityRepository loginActivityRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.loginActivityRepository = loginActivityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        if (request.getUsername() != null &&
                ("admin".equalsIgnoreCase(request.getUsername().trim()) || "superadmin".equalsIgnoreCase(request.getUsername().trim()))) {
            throw new IllegalArgumentException("Username '" + request.getUsername().trim() + "' is reserved for system administration.");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("Phone number is already registered");
        }
        if (userRepository.existsByPan(request.getPan())) {
            throw new IllegalArgumentException("PAN Card is already registered");
        }
        if (userRepository.existsByAadhar(request.getAadhar())) {
            throw new IllegalArgumentException("Aadhar Card is already registered");
        }

        // Strict RBAC: All registered users are assigned ROLE_CUSTOMER
        Role role = Role.ROLE_CUSTOMER;

        // Build User
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .fatherName(request.getFatherName())
                .motherName(request.getMotherName())
                .dob(request.getDob())
                .pan(request.getPan().toUpperCase())
                .aadhar(request.getAadhar())
                .gender(Gender.valueOf(request.getGender().toUpperCase()))
                .address(request.getAddress())
                .role(role)
                .build();

        User savedUser = userRepository.save(user);

        // Auto-create a default bank account for the user
        String accountNumber = generateUniqueAccountNumber();
        BigDecimal initialBal = request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO;
        
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ifscCode("OBIN0000101")
                .accountType(AccountType.valueOf(request.getAccountType().toUpperCase()))
                .balance(initialBal)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .accountHolderName(savedUser.getFullName())
                .user(savedUser)
                .build();

        accountRepository.save(account);

        return new RegisterResponse(savedUser.getUsername(), accountNumber, "User registered successfully");
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User updateProfile(String username, ProfileUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.project.onlinebankingsystem.exception.ResourceNotFoundException("User not found"));

        if (!user.getEmail().equalsIgnoreCase(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (!user.getPhone().equals(request.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("Phone number is already registered");
        }

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setFatherName(request.getFatherName());
        user.setMotherName(request.getMotherName());
        user.setDob(request.getDob());
        user.setGender(Gender.valueOf(request.getGender().toUpperCase()));
        user.setAddress(request.getAddress());

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.project.onlinebankingsystem.exception.ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateTransactionPin(String username, String pin, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.project.onlinebankingsystem.exception.ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect account password. Verification failed.");
        }

        if (pin == null || !pin.matches("^[0-9]{4}$")) {
            throw new IllegalArgumentException("PIN must be exactly 4 numeric digits.");
        }

        user.setTransactionPin(passwordEncoder.encode(pin));
        userRepository.save(user);
    }

    @Transactional
    public void recordLoginSuccess(String username, String ipAddress, String userAgent) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLastLoginTime(LocalDateTime.now());
            user.setAccountSecurityStatus("GOOD");
            userRepository.save(user);
        });

        LoginActivity activity = LoginActivity.builder()
                .username(username)
                .ipAddress(ipAddress != null ? ipAddress : "127.0.0.1")
                .userAgent(userAgent != null ? (userAgent.length() > 250 ? userAgent.substring(0, 250) : userAgent) : "Browser")
                .timestamp(LocalDateTime.now())
                .status("SUCCESS")
                .build();
        loginActivityRepository.save(activity);
    }

    @Transactional
    public void recordLoginFailure(String username, String ipAddress, String userAgent, String reason) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() + 1 : 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 3) {
                user.setAccountSecurityStatus("ALERT");
            }
            userRepository.save(user);
        });

        LoginActivity activity = LoginActivity.builder()
                .username(username != null ? username : "anonymous")
                .ipAddress(ipAddress != null ? ipAddress : "127.0.0.1")
                .userAgent(userAgent != null ? (userAgent.length() > 250 ? userAgent.substring(0, 250) : userAgent) : "Browser")
                .timestamp(LocalDateTime.now())
                .status("FAILED")
                .failureReason(reason != null ? reason : "Bad credentials")
                .build();
        loginActivityRepository.save(activity);
    }

    public List<LoginActivity> getRecentLoginActivities(String username) {
        return loginActivityRepository.findTop10ByUsernameOrderByTimestampDesc(username);
    }

    public int calculateSecurityScore(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return 70;

        int score = 100;
        if (user.getTransactionPin() == null || user.getTransactionPin().isBlank()) {
            score -= 15;
        }
        if (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) {
            score -= Math.min(30, user.getFailedLoginAttempts() * 15);
        }
        if ("ALERT".equalsIgnoreCase(user.getAccountSecurityStatus())) {
            score -= 20;
        }
        return Math.max(30, Math.min(100, score));
    }

    private String generateUniqueAccountNumber() {
        Random random = new Random();
        String accountNumber;
        do {
            // Generate a 10-digit account number as a string
            long num = 1000000000L + (long)(random.nextDouble() * 9000000000L);
            accountNumber = String.valueOf(num);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
