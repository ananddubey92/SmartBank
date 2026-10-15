package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.DebitCardBackfillResultDTO;
import com.project.onlinebankingsystem.dto.DebitCardResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.CardStatus;
import com.project.onlinebankingsystem.enums.CardType;
import com.project.onlinebankingsystem.enums.NotificationType;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.DebitCard;
import com.project.onlinebankingsystem.model.Notification;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.DebitCardRepository;
import com.project.onlinebankingsystem.repository.NotificationRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class DebitCardService {

    private static final Logger log = LoggerFactory.getLogger(DebitCardService.class);
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("MM/yy");
    private static final Random RANDOM = new SecureRandom();

    private final DebitCardRepository debitCardRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public DebitCardService(DebitCardRepository debitCardRepository,
                            AccountRepository accountRepository,
                            UserRepository userRepository,
                            NotificationRepository notificationRepository,
                            AuditLogService auditLogService,
                            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.debitCardRepository = debitCardRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generates a unique Debit Card record for an eligible bank account.
     * Idempotent: If a card is already linked, returns the existing card without duplicate creation.
     */
    @Transactional
    public DebitCard generateDebitCardForAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null.");
        }

        // Check if debit card already exists for this account
        Optional<DebitCard> existingCard = debitCardRepository.findByAccount(account);
        if (existingCard.isPresent()) {
            log.info("Account #{} already has Debit Card linked (Card ID: {}). Skipping duplicate creation.",
                    account.getAccountNumber(), existingCard.get().getId());
            return existingCard.get();
        }

        // Validate account eligibility
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(String.format(
                    "Cannot issue Debit Card: Account #%s is %s. Only ACTIVE accounts are eligible.",
                    account.getAccountNumber(), account.getStatus()));
        }

        User user = account.getUser();
        String holderName = account.getAccountHolderName();
        if (holderName == null || holderName.isBlank()) {
            holderName = (user != null) ? user.getFullName() : "VALUED CUSTOMER";
        }

        // Generate unique 16-digit card number (Simulated RuPay Platinum BIN: 6075 24xx xxxx xxxx)
        String rawCardNumber = generateUniqueCardNumber();
        String maskedCardNumber = DebitCard.maskCardNumber(rawCardNumber);
        String cvv = String.format("%03d", 100 + RANDOM.nextInt(900));

        LocalDate issueDate = LocalDate.now();
        LocalDate expiryDate = issueDate.plusYears(5);

        DebitCard debitCard = DebitCard.builder()
                .cardNumber(rawCardNumber)
                .maskedCardNumber(maskedCardNumber)
                .cardHolderName(holderName.toUpperCase())
                .cvv(cvv)
                .cardType(CardType.RUPAY_PLATINUM)
                .network("RUPAY")
                .issueDate(issueDate)
                .expiryDate(expiryDate)
                .status(CardStatus.ACTIVE)
                .account(account)
                .user(user)
                .dailyWithdrawalLimit(new BigDecimal("50000.00"))
                .dailyOnlineLimit(new BigDecimal("100000.00"))
                .internationalUsage(false)
                .onlineTransactions(true)
                .contactlessEnabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        DebitCard saved = debitCardRepository.save(debitCard);

        // Notify customer
        if (user != null) {
            try {
                notificationRepository.save(Notification.builder()
                        .user(user)
                        .type(NotificationType.SECURITY_ALERT)
                        .title("Debit Card Issued & Activated")
                        .message(String.format("Your RuPay Platinum Debit Card (%s) linked to account #%s has been successfully issued and activated.",
                                maskedCardNumber, account.getAccountNumber()))
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .actionUrl("/dashboard")
                        .build());
            } catch (Exception e) {
                log.warn("Failed to create issuance notification for user {}: {}", user.getUsername(), e.getMessage());
            }
        }

        log.info("Successfully generated Debit Card {} for Account #{}", maskedCardNumber, account.getAccountNumber());
        return saved;
    }

    /**
     * Super Admin Backfill:
     * Scans all existing accounts in database.
     * Generates debit cards for eligible accounts lacking a card.
     * Skips accounts that already have cards.
     * Skips/flags ineligible accounts (SUSPENDED, CLOSED).
     * Fully idempotent — safe to re-run anytime.
     */
    @Transactional
    public DebitCardBackfillResultDTO backfillMissingDebitCards(String performedBy, String ipAddress) {
        log.info("Starting Debit Card Backfill initiated by '{}' from IP '{}'...", performedBy, ipAddress);

        List<Account> allAccounts = accountRepository.findAll();
        int totalScanned = allAccounts.size();
        int eligibleCount = 0;
        int generatedCount = 0;
        int skippedAlreadyHas = 0;
        int ineligibleSkipped = 0;
        int failedCount = 0;

        List<DebitCardResponse> newlyGeneratedCards = new ArrayList<>();
        List<DebitCardBackfillResultDTO.BackfillItemDetail> itemDetails = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (Account account : allAccounts) {
            String accNum = account.getAccountNumber();
            String username = (account.getUser() != null) ? account.getUser().getUsername() : "N/A";
            String holder = (account.getAccountHolderName() != null) ? account.getAccountHolderName() :
                    ((account.getUser() != null) ? account.getUser().getFullName() : "N/A");

            // 1. Check if card already exists
            Optional<DebitCard> existingOpt = debitCardRepository.findByAccount(account);
            if (existingOpt.isPresent()) {
                skippedAlreadyHas++;
                DebitCard card = existingOpt.get();
                itemDetails.add(DebitCardBackfillResultDTO.BackfillItemDetail.builder()
                        .accountNumber(accNum)
                        .customerUsername(username)
                        .customerName(holder)
                        .accountStatus(account.getStatus().name())
                        .result("SKIPPED_ALREADY_EXISTS")
                        .maskedCardNumber(card.getMaskedCardNumber())
                        .message("Debit Card already linked (" + card.getStatus() + "). Skipped duplicate creation.")
                        .build());
                continue;
            }

            // 2. Check account eligibility
            if (account.getStatus() != AccountStatus.ACTIVE) {
                ineligibleSkipped++;
                itemDetails.add(DebitCardBackfillResultDTO.BackfillItemDetail.builder()
                        .accountNumber(accNum)
                        .customerUsername(username)
                        .customerName(holder)
                        .accountStatus(account.getStatus().name())
                        .result("SKIPPED_INELIGIBLE")
                        .maskedCardNumber("N/A")
                        .message("Account is " + account.getStatus() + ". Card generation withheld per compliance rules.")
                        .build());
                continue;
            }

            // 3. Eligible account missing card -> Generate
            eligibleCount++;
            try {
                DebitCard newCard = generateDebitCardForAccount(account);
                generatedCount++;
                DebitCardResponse resp = convertToResponse(newCard);
                newlyGeneratedCards.add(resp);

                itemDetails.add(DebitCardBackfillResultDTO.BackfillItemDetail.builder()
                        .accountNumber(accNum)
                        .customerUsername(username)
                        .customerName(holder)
                        .accountStatus(account.getStatus().name())
                        .result("GENERATED")
                        .maskedCardNumber(newCard.getMaskedCardNumber())
                        .message("New RuPay Platinum Debit Card generated successfully.")
                        .build());

            } catch (Exception e) {
                failedCount++;
                String errMsg = String.format("Account #%s (%s): %s", accNum, username, e.getMessage());
                errorMessages.add(errMsg);
                log.error("Failed to generate Debit Card for account #{}: {}", accNum, e.getMessage(), e);

                itemDetails.add(DebitCardBackfillResultDTO.BackfillItemDetail.builder()
                        .accountNumber(accNum)
                        .customerUsername(username)
                        .customerName(holder)
                        .accountStatus(account.getStatus().name())
                        .result("FAILED")
                        .maskedCardNumber("N/A")
                        .message("Generation failed: " + e.getMessage())
                        .build());
            }
        }

        String overallStatus = (failedCount > 0)
                ? (generatedCount > 0 ? "PARTIAL_SUCCESS" : "FAILED")
                : (generatedCount > 0 ? "SUCCESS" : "COMPLETED_NO_OP");

        String summary = String.format(
                "Backfill complete: Scanned %d accounts. Generated %d cards. Skipped %d existing cards. Skipped %d ineligible accounts. Failures: %d.",
                totalScanned, generatedCount, skippedAlreadyHas, ineligibleSkipped, failedCount);

        // Audit the backfill execution
        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "DEBIT_CARDS_BACKFILL",
                "DEBIT_CARD",
                "BATCH",
                "NONE",
                overallStatus,
                ipAddress,
                failedCount == 0 ? "SUCCESS" : "WARNING",
                summary
        );

        return DebitCardBackfillResultDTO.builder()
                .totalAccountsScanned(totalScanned)
                .eligibleAccountsCount(eligibleCount)
                .generatedCount(generatedCount)
                .skippedCount(skippedAlreadyHas)
                .ineligibleSkippedCount(ineligibleSkipped)
                .failedCount(failedCount)
                .status(overallStatus)
                .summaryMessage(summary)
                .generatedCards(newlyGeneratedCards)
                .processedDetails(itemDetails)
                .errorMessages(errorMessages)
                .build();
    }

    /**
     * Retrieve the debit card for the currently logged-in customer.
     * Customer can only access their own debit card.
     */
    public Optional<DebitCardResponse> getCardForCustomer(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + username));

        List<DebitCard> userCards = debitCardRepository.findByUser(user);
        if (!userCards.isEmpty()) {
            return Optional.of(convertToResponse(userCards.get(0)));
        }

        // If user has an active account but somehow no card was generated, auto-issue it
        List<Account> accounts = accountRepository.findByUser(user);
        for (Account acc : accounts) {
            if (acc.getStatus() == AccountStatus.ACTIVE) {
                try {
                    DebitCard autoCard = generateDebitCardForAccount(acc);
                    return Optional.of(convertToResponse(autoCard));
                } catch (Exception e) {
                    log.warn("Could not auto-provision debit card for account #{}: {}", acc.getAccountNumber(), e.getMessage());
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieve all cards belonging to the logged-in customer.
     */
    public List<DebitCardResponse> getCardsForCustomer(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + username));

        List<DebitCard> cards = debitCardRepository.findByUser(user);
        if (cards.isEmpty()) {
            // Check if user has active accounts without card
            List<Account> accounts = accountRepository.findByUser(user);
            for (Account acc : accounts) {
                if (acc.getStatus() == AccountStatus.ACTIVE) {
                    try {
                        DebitCard newCard = generateDebitCardForAccount(acc);
                        cards.add(newCard);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return cards.stream().map(this::convertToResponse).toList();
    }

    /**
     * Customer toggles their own card status between ACTIVE and BLOCKED.
     */
    @Transactional
    public DebitCardResponse toggleCardStatusByCustomer(Long cardId, String username, String ipAddress) {
        DebitCard card = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit card not found with ID: " + cardId));

        if (!card.getUser().getUsername().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("Access denied: You can only manage your own Debit Card.");
        }

        CardStatus oldStatus = card.getStatus();
        CardStatus newStatus = (oldStatus == CardStatus.ACTIVE) ? CardStatus.BLOCKED : CardStatus.ACTIVE;
        card.setStatus(newStatus);
        card.setUpdatedAt(LocalDateTime.now());
        DebitCard saved = debitCardRepository.save(card);

        // Log audit
        auditLogService.logAction(
                username,
                "ROLE_CUSTOMER",
                "CARD_STATUS_UPDATE",
                "DEBIT_CARD",
                saved.getMaskedCardNumber(),
                oldStatus.name(),
                newStatus.name(),
                ipAddress,
                "SUCCESS",
                String.format("Customer %s updated Debit Card status to %s", username, newStatus)
        );

        // Notify user
        try {
            notificationRepository.save(Notification.builder()
                    .user(card.getUser())
                    .type(newStatus == CardStatus.BLOCKED ? NotificationType.SECURITY_ALERT : NotificationType.TRANSACTION_SUCCESS)
                    .title("Debit Card Status Changed")
                    .message(String.format("Your Debit Card %s status has been changed to %s.",
                            saved.getMaskedCardNumber(), newStatus))
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .actionUrl("/dashboard")
                    .build());
        } catch (Exception ignored) {
        }

        return convertToResponse(saved);
    }

    /**
     * Super Admin updates status for any debit card.
     */
    @Transactional
    public DebitCardResponse updateCardStatusByAdmin(Long cardId, CardStatus newStatus, String performedBy, String ipAddress, String reason) {
        DebitCard card = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit card not found with ID: " + cardId));

        CardStatus oldStatus = card.getStatus();
        card.setStatus(newStatus);
        card.setUpdatedAt(LocalDateTime.now());
        DebitCard saved = debitCardRepository.save(card);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "CARD_STATUS_CHANGE",
                "DEBIT_CARD",
                saved.getMaskedCardNumber(),
                oldStatus.name(),
                newStatus.name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin changed Debit Card status to %s. Reason: %s", newStatus, (reason != null ? reason : "Administrative"))
        );

        return convertToResponse(saved);
    }

    /**
     * Get all debit cards for administrative inspection.
     */
    public List<DebitCardResponse> getAllDebitCards() {
        return debitCardRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * Convert DebitCard entity to secure response DTO (Masked by default).
     */
    public DebitCardResponse convertToResponse(DebitCard card) {
        return convertToResponse(card, false);
    }

    /**
     * Convert DebitCard entity to response DTO with optional full credentials reveal.
     */
    public DebitCardResponse convertToResponse(DebitCard card, boolean unmasked) {
        if (card == null) return null;

        String expiryFormatted = (card.getExpiryDate() != null)
                ? card.getExpiryDate().format(EXPIRY_FORMATTER) : "N/A";

        String rawCardNumber = card.getCardNumber();
        String formatted = formatCardNumber(rawCardNumber);

        return DebitCardResponse.builder()
                .id(card.getId())
                .cardNumber(card.getMaskedCardNumber())
                .rawCardNumber(unmasked ? rawCardNumber : card.getMaskedCardNumber())
                .formattedCardNumber(unmasked ? formatted : card.getMaskedCardNumber())
                .cvv(unmasked ? card.getCvv() : "***")
                .lastFourDigits(card.getLastFourDigits())
                .cardHolderName(card.getCardHolderName())
                .cardType(card.getCardType() != null ? card.getCardType().getDisplayName() : "RuPay Platinum")
                .network(card.getNetwork() != null ? card.getNetwork() : "RUPAY")
                .expiryDateFormatted(expiryFormatted)
                .issueDate(card.getIssueDate())
                .expiryDate(card.getExpiryDate())
                .status(card.getStatus().name())
                .accountNumber(card.getAccount() != null ? card.getAccount().getAccountNumber() : "N/A")
                .accountType(card.getAccount() != null ? card.getAccount().getAccountType().name() : "N/A")
                .username(card.getUser() != null ? card.getUser().getUsername() : "N/A")
                .dailyWithdrawalLimit(card.getDailyWithdrawalLimit())
                .dailyOnlineLimit(card.getDailyOnlineLimit())
                .internationalUsage(card.getInternationalUsage())
                .onlineTransactions(card.getOnlineTransactions())
                .contactlessEnabled(card.getContactlessEnabled())
                .createdAt(card.getCreatedAt())
                .build();
    }

    /**
     * Re-authenticated reveal: Customer supplies Security PIN or Account Password to reveal full 16-digit card number and CVV.
     * Generates a permanent security audit trail of the reveal event.
     */
    @Transactional
    public DebitCardResponse revealCardDetails(Long cardId, String username, String pinOrPassword, String ipAddress) {
        DebitCard card = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit card not found with ID: " + cardId));

        if (!card.getUser().getUsername().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("Access denied: You can only reveal your own Debit Card.");
        }

        User user = card.getUser();
        boolean authenticated = false;
        if (pinOrPassword != null && !pinOrPassword.isBlank()) {
            String cred = pinOrPassword.trim();
            if (user.getTransactionPin() != null && passwordEncoder.matches(cred, user.getTransactionPin())) {
                authenticated = true;
            } else if (passwordEncoder.matches(cred, user.getPassword())) {
                authenticated = true;
            }
        }

        if (!authenticated) {
            auditLogService.logAction(
                    username,
                    "ROLE_CUSTOMER",
                    "CARD_REVEAL_FAILED",
                    "DEBIT_CARD",
                    card.getMaskedCardNumber(),
                    "MASKED",
                    "FAILED",
                    ipAddress,
                    "FAILED",
                    "Unauthorized card credential reveal attempt with invalid PIN/Password"
            );
            throw new AccessDeniedException("Authentication failed: Invalid Security PIN or Password for debit card reveal.");
        }

        auditLogService.logAction(
                username,
                "ROLE_CUSTOMER",
                "CARD_REVEAL_SUCCESS",
                "DEBIT_CARD",
                card.getMaskedCardNumber(),
                "MASKED",
                "REVEALED",
                ipAddress,
                "SUCCESS",
                "Customer re-authenticated successfully and revealed full card credentials"
        );

        return convertToResponse(card, true);
    }

    /**
     * Cancels any existing debit card linked to an account (e.g. on account suspension/closure).
     */
    @Transactional
    public void cancelCardForAccount(Account account, String performedBy, String ipAddress, String reason) {
        Optional<DebitCard> cardOpt = debitCardRepository.findByAccount(account);
        if (cardOpt.isPresent()) {
            DebitCard card = cardOpt.get();
            CardStatus prev = card.getStatus();
            card.setStatus(CardStatus.CANCELLED);
            card.setUpdatedAt(LocalDateTime.now());
            debitCardRepository.save(card);

            auditLogService.logAction(
                    performedBy,
                    "ROLE_SUPER_ADMIN",
                    "CARD_CANCELLED",
                    "DEBIT_CARD",
                    card.getMaskedCardNumber(),
                    prev.name(),
                    CardStatus.CANCELLED.name(),
                    ipAddress,
                    "SUCCESS",
                    "Debit Card cancelled due to account state change: " + reason
            );
        }
    }

    /**
     * Card replacement workflow: cancels existing card and issues a new one.
     */
    @Transactional
    public DebitCardResponse replaceCard(Long cardId, String username, String ipAddress) {
        DebitCard oldCard = debitCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit card not found with ID: " + cardId));

        if (!oldCard.getUser().getUsername().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("Access denied: You can only replace your own Debit Card.");
        }

        Account account = oldCard.getAccount();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot replace card: Linked account is not ACTIVE.");
        }

        oldCard.setStatus(CardStatus.CANCELLED);
        oldCard.setUpdatedAt(LocalDateTime.now());
        debitCardRepository.save(oldCard);

        // Delete old card relationship to allow new card generation
        debitCardRepository.delete(oldCard);

        DebitCard newCard = generateDebitCardForAccount(account);

        auditLogService.logAction(
                username,
                "ROLE_CUSTOMER",
                "CARD_REPLACEMENT",
                "DEBIT_CARD",
                newCard.getMaskedCardNumber(),
                oldCard.getMaskedCardNumber(),
                newCard.getMaskedCardNumber(),
                ipAddress,
                "SUCCESS",
                "Customer requested debit card replacement. Old card cancelled, new card issued."
        );

        return convertToResponse(newCard);
    }

    public String formatCardNumber(String raw) {
        if (raw == null || raw.length() != 16) return raw;
        return raw.substring(0, 4) + " " + raw.substring(4, 8) + " " + raw.substring(8, 12) + " " + raw.substring(12, 16);
    }

    /**
     * Generates a 16-digit card number with RuPay Platinum BIN prefix (6075 24xx xxxx xxxx).
     * Ensures strict global uniqueness against the database.
     */
    private String generateUniqueCardNumber() {
        String cardNumber;
        int attempts = 0;
        do {
            // Prefix: 6075 24 (6 digits) + 10 random digits = 16 digits
            long randomPart = 1000000000L + (long)(RANDOM.nextDouble() * 9000000000L);
            cardNumber = "607524" + randomPart;
            attempts++;
            if (attempts > 50) {
                // Fallback to random 16-digit
                long p1 = 60000000L + (long)(RANDOM.nextDouble() * 39999999L);
                long p2 = 10000000L + (long)(RANDOM.nextDouble() * 89999999L);
                cardNumber = String.valueOf(p1) + String.valueOf(p2);
            }
        } while (debitCardRepository.existsByCardNumber(cardNumber));

        return cardNumber;
    }
}
