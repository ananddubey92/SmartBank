package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.BeneficiaryRequest;
import com.project.onlinebankingsystem.enums.NotificationType;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Beneficiary;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.BeneficiaryRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository,
                              UserRepository userRepository,
                              NotificationService notificationService) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Beneficiary addBeneficiary(String username, BeneficiaryRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Beneficiary beneficiary = Beneficiary.builder()
                .name(request.getName())
                .accountNumber(request.getAccountNumber())
                .ifscCode(request.getIfscCode())
                .bankName(request.getBankName() != null && !request.getBankName().isBlank() ? request.getBankName() : "External Bank")
                .user(user)
                .createdAt(LocalDateTime.now())
                .confirmed(true)
                .transferCount(0)
                .totalTransferred(BigDecimal.ZERO)
                .riskStatus("NEW")
                .build();

        Beneficiary saved = beneficiaryRepository.save(beneficiary);

        notificationService.createNotification(user, NotificationType.BENEFICIARY_ADDED,
                "New Beneficiary Registered",
                String.format("Beneficiary %s (%s) was registered. Note: transfers within the first 24h will be subject to AI step-up verification.",
                        request.getName(), request.getAccountNumber()),
                "/beneficiaries");

        return saved;
    }

    public List<Beneficiary> getBeneficiariesByUsername(String username) {
        return beneficiaryRepository.findByUserUsername(username);
    }

    @Transactional
    public void deleteBeneficiary(Long beneficiaryId, String username) {
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));

        if (!beneficiary.getUser().getUsername().equals(username)) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized to delete this beneficiary");
        }

        beneficiaryRepository.delete(beneficiary);
    }
}
