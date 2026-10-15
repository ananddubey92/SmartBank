package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.DebitCardBackfillResultDTO;
import com.project.onlinebankingsystem.dto.DebitCardResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import com.project.onlinebankingsystem.enums.CardStatus;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.DebitCard;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.DebitCardRepository;
import com.project.onlinebankingsystem.repository.NotificationRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DebitCardServiceTest {

    @Mock
    private DebitCardRepository debitCardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DebitCardService debitCardService;

    private User customerUser;
    private User otherCustomer;
    private Account account1;
    private Account account2;
    private Account closedAccount;

    @BeforeEach
    void setUp() {
        customerUser = User.builder()
                .id(1L)
                .username("rahul")
                .fullName("Rahul Sharma")
                .email("rahul@example.com")
                .role(Role.ROLE_CUSTOMER)
                .build();

        otherCustomer = User.builder()
                .id(2L)
                .username("priya")
                .fullName("Priya Patel")
                .email("priya@example.com")
                .role(Role.ROLE_CUSTOMER)
                .build();

        account1 = Account.builder()
                .id(101L)
                .accountNumber("1002345678")
                .ifscCode("OBIN0000101")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("25000.00"))
                .status(AccountStatus.ACTIVE)
                .accountHolderName("Rahul Sharma")
                .user(customerUser)
                .build();

        account2 = Account.builder()
                .id(102L)
                .accountNumber("2009876543")
                .ifscCode("OBIN0000101")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("15000.00"))
                .status(AccountStatus.ACTIVE)
                .accountHolderName("Priya Patel")
                .user(otherCustomer)
                .build();

        closedAccount = Account.builder()
                .id(103L)
                .accountNumber("3005556667")
                .ifscCode("OBIN0000101")
                .accountType(AccountType.CURRENT)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.CLOSED)
                .accountHolderName("Old Account")
                .user(customerUser)
                .build();
    }

    @Test
    @DisplayName("Generate card: Creates unique active debit card for eligible active account")
    void generateDebitCard_createsActiveCard() {
        when(debitCardRepository.findByAccount(account1)).thenReturn(Optional.empty());
        when(debitCardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(debitCardRepository.save(any(DebitCard.class))).thenAnswer(invocation -> {
            DebitCard card = invocation.getArgument(0);
            card.setId(10L);
            return card;
        });

        DebitCard card = debitCardService.generateDebitCardForAccount(account1);

        assertNotNull(card);
        assertEquals(CardStatus.ACTIVE, card.getStatus());
        assertTrue(card.getCardNumber().startsWith("607524"));
        assertEquals(16, card.getCardNumber().length());
        assertTrue(card.getMaskedCardNumber().startsWith("**** **** **** "));
        assertEquals(account1, card.getAccount());
        assertEquals(customerUser, card.getUser());
        assertEquals("RAHUL SHARMA", card.getCardHolderName());

        verify(debitCardRepository, times(1)).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("Generate card: Idempotent return of existing card if account already has card linked")
    void generateDebitCard_returnsExistingCardIfAlreadyPresent() {
        DebitCard existing = DebitCard.builder()
                .id(5L)
                .cardNumber("6075241234567890")
                .maskedCardNumber("**** **** **** 7890")
                .status(CardStatus.ACTIVE)
                .account(account1)
                .user(customerUser)
                .build();

        when(debitCardRepository.findByAccount(account1)).thenReturn(Optional.of(existing));

        DebitCard card = debitCardService.generateDebitCardForAccount(account1);

        assertEquals(5L, card.getId());
        verify(debitCardRepository, never()).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("Generate card: Ineligible account (CLOSED or SUSPENDED) throws exception")
    void generateDebitCard_ineligibleAccount_throwsException() {
        when(debitCardRepository.findByAccount(closedAccount)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> {
            debitCardService.generateDebitCardForAccount(closedAccount);
        });
        verify(debitCardRepository, never()).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("Backfill: Idempotency scenario - 3 accounts, 1 already has card, 1 closed -> 1 new generated, 1 skipped, 1 ineligible")
    void backfill_idempotentPartialScenario() {
        DebitCard existingForAccount1 = DebitCard.builder()
                .id(1L)
                .cardNumber("6075241111222233")
                .maskedCardNumber("**** **** **** 2233")
                .status(CardStatus.ACTIVE)
                .account(account1)
                .user(customerUser)
                .build();

        when(accountRepository.findAll()).thenReturn(List.of(account1, account2, closedAccount));
        when(debitCardRepository.findByAccount(account1)).thenReturn(Optional.of(existingForAccount1));
        when(debitCardRepository.findByAccount(account2)).thenReturn(Optional.empty());
        when(debitCardRepository.findByAccount(closedAccount)).thenReturn(Optional.empty());

        when(debitCardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(debitCardRepository.save(any(DebitCard.class))).thenAnswer(invocation -> {
            DebitCard c = invocation.getArgument(0);
            c.setId(2L);
            return c;
        });

        DebitCardBackfillResultDTO result = debitCardService.backfillMissingDebitCards("superadmin", "127.0.0.1");

        assertNotNull(result);
        assertEquals(3, result.getTotalAccountsScanned());
        assertEquals(1, result.getGeneratedCount());       // account2
        assertEquals(1, result.getSkippedCount());         // account1
        assertEquals(1, result.getIneligibleSkippedCount()); // closedAccount
        assertEquals(0, result.getFailedCount());
        assertEquals("SUCCESS", result.getStatus());

        // Verify that account2 received a card, while account1 and closedAccount did not create new cards
        verify(debitCardRepository, times(1)).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("Backfill: Re-running backfill when all eligible accounts already have cards generates 0 new cards")
    void backfill_rerunAllExisting_generatesZeroNewCards() {
        DebitCard card1 = DebitCard.builder().id(1L).cardNumber("6075241111111111").maskedCardNumber("**** **** **** 1111").status(CardStatus.ACTIVE).account(account1).user(customerUser).build();
        DebitCard card2 = DebitCard.builder().id(2L).cardNumber("6075242222222222").maskedCardNumber("**** **** **** 2222").status(CardStatus.ACTIVE).account(account2).user(otherCustomer).build();

        when(accountRepository.findAll()).thenReturn(List.of(account1, account2));
        when(debitCardRepository.findByAccount(account1)).thenReturn(Optional.of(card1));
        when(debitCardRepository.findByAccount(account2)).thenReturn(Optional.of(card2));

        DebitCardBackfillResultDTO result = debitCardService.backfillMissingDebitCards("superadmin", "127.0.0.1");

        assertEquals(2, result.getTotalAccountsScanned());
        assertEquals(0, result.getGeneratedCount());
        assertEquals(2, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals("COMPLETED_NO_OP", result.getStatus());
        verify(debitCardRepository, never()).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("Customer authorization: Customer cannot toggle another user's debit card")
    void toggleCardStatus_unauthorizedUser_throwsAccessDenied() {
        DebitCard priyaCard = DebitCard.builder()
                .id(20L)
                .cardNumber("6075249999888877")
                .maskedCardNumber("**** **** **** 8877")
                .status(CardStatus.ACTIVE)
                .account(account2)
                .user(otherCustomer) // owned by priya
                .build();

        when(debitCardRepository.findById(20L)).thenReturn(Optional.of(priyaCard));

        // rahul attempts to modify priya's card
        assertThrows(AccessDeniedException.class, () -> {
            debitCardService.toggleCardStatusByCustomer(20L, "rahul", "127.0.0.1");
        });

        verify(debitCardRepository, never()).save(any(DebitCard.class));
    }

    @Test
    @DisplayName("Customer authorization: Customer can toggle their own debit card from ACTIVE to BLOCKED")
    void toggleCardStatus_ownerTogglesCard_success() {
        DebitCard rahulCard = DebitCard.builder()
                .id(15L)
                .cardNumber("6075241234123456")
                .maskedCardNumber("**** **** **** 3456")
                .status(CardStatus.ACTIVE)
                .account(account1)
                .user(customerUser)
                .build();

        when(debitCardRepository.findById(15L)).thenReturn(Optional.of(rahulCard));
        when(debitCardRepository.save(any(DebitCard.class))).thenAnswer(i -> i.getArgument(0));

        DebitCardResponse response = debitCardService.toggleCardStatusByCustomer(15L, "rahul", "127.0.0.1");

        assertNotNull(response);
        assertEquals(CardStatus.BLOCKED.name(), response.getStatus());
        verify(debitCardRepository, times(1)).save(rahulCard);
    }
}
