package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.AccountRequest;
import com.project.onlinebankingsystem.dto.AccountResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.DebitCard;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.DebitCardRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final DebitCardRepository debitCardRepository;
    private final DebitCardService debitCardService;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          DebitCardRepository debitCardRepository,
                          @Lazy DebitCardService debitCardService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.debitCardRepository = debitCardRepository;
        this.debitCardService = debitCardService;
    }

    @Transactional
    public AccountResponse createAccount(String username, AccountRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        String accountNumber = generateUniqueAccountNumber();

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ifscCode("OBIN0000101")
                .accountType(AccountType.valueOf(request.getAccountType().toUpperCase()))
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .accountHolderName(user.getFullName())
                .user(user)
                .build();

        Account saved = accountRepository.save(account);

        // Auto-generate Debit Card for newly created account
        try {
            debitCardService.generateDebitCardForAccount(saved);
        } catch (Exception e) {
            log.warn("Automatic debit card provisioning during customer account opening: {}", e.getMessage());
        }

        return convertToResponse(saved);
    }

    public AccountResponse getAccountDetails(String accountNumber) {
        return getAccountDetails(accountNumber, null);
    }

    public AccountResponse getAccountDetails(String accountNumber, String username) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        if (username != null) {
            User caller = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
            boolean isStaff = caller.getRole() == com.project.onlinebankingsystem.enums.Role.ROLE_ADMIN 
                    || caller.getRole() == com.project.onlinebankingsystem.enums.Role.ROLE_SUPER_ADMIN;
            boolean isOwner = account.getUser() != null && account.getUser().getUsername().equalsIgnoreCase(username);
            if (!isStaff && !isOwner) {
                throw new org.springframework.security.access.AccessDeniedException("Unauthorized access: You can only view your own account details.");
            }
        }

        return convertToResponse(account);
    }

    public List<AccountResponse> getAccountsByUsername(String username) {
        List<Account> accounts = accountRepository.findByUserUsername(username);
        return accounts.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public AccountResponse convertToResponse(Account account) {
        String holderName = account.getAccountHolderName() != null ? account.getAccountHolderName() :
                (account.getUser() != null ? account.getUser().getFullName() : null);

        boolean hasCard = false;
        String cardNum = null;
        String cardStatus = null;

        if (account.getDebitCard() != null) {
            hasCard = true;
            cardNum = account.getDebitCard().getMaskedCardNumber();
            cardStatus = account.getDebitCard().getStatus().name();
        } else {
            Optional<DebitCard> cardOpt = debitCardRepository.findByAccount(account);
            if (cardOpt.isPresent()) {
                hasCard = true;
                cardNum = cardOpt.get().getMaskedCardNumber();
                cardStatus = cardOpt.get().getStatus().name();
            }
        }

        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ifscCode(account.getIfscCode())
                .accountType(account.getAccountType().name())
                .balance(account.getBalance())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .accountHolderName(holderName)
                .userUsername(account.getUser() != null ? account.getUser().getUsername() : null)
                .userFullName(holderName)
                .userEmail(account.getUser() != null ? account.getUser().getEmail() : null)
                .userPhone(account.getUser() != null ? account.getUser().getPhone() : null)
                .userPan(account.getUser() != null ? account.getUser().getMaskedPan() : null)
                .userAadhar(account.getUser() != null ? account.getUser().getMaskedAadhar() : null)
                .nomineeName(account.getNomineeName())
                .nomineeRelationship(account.getNomineeRelationship())
                .hasDebitCard(hasCard)
                .debitCardNumber(cardNum)
                .debitCardStatus(cardStatus)
                .branchName(account.getBranchName() != null ? account.getBranchName() : "Main Branch, Mumbai")
                .userPinCode(account.getUser() != null ? account.getUser().getPinCode() : null)
                .userKycStatus(account.getUser() != null && account.getUser().getKycStatus() != null ? account.getUser().getKycStatus().name() : "VERIFIED")
                .userFatherName(account.getUser() != null ? account.getUser().getFatherName() : null)
                .userMotherName(account.getUser() != null ? account.getUser().getMotherName() : null)
                .userDob(account.getUser() != null ? account.getUser().getDob() : null)
                .userGender(account.getUser() != null && account.getUser().getGender() != null ? account.getUser().getGender().name() : null)
                .userAddress(account.getUser() != null ? account.getUser().getAddress() : null)
                .build();
    }

    private String generateUniqueAccountNumber() {
        Random random = new Random();
        String accountNumber;
        do {
            long num = 1000000000L + (long)(random.nextDouble() * 9000000000L);
            accountNumber = String.valueOf(num);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
