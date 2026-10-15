package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.StatementResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StatementService statementService;

    private User superAdminUser;
    private User customerUser1;
    private User customerUser2;
    private Account account1;
    private Account account2;

    @BeforeEach
    void setUp() {
        superAdminUser = User.builder()
                .id(1L)
                .username("superadmin")
                .fullName("Super Admin")
                .email("superadmin@bank.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        customerUser1 = User.builder()
                .id(2L)
                .username("rahul")
                .fullName("Rahul Sharma")
                .email("rahul@example.com")
                .phone("9876543210")
                .role(Role.ROLE_CUSTOMER)
                .build();

        customerUser2 = User.builder()
                .id(3L)
                .username("priya")
                .fullName("Priya Patel")
                .email("priya@example.com")
                .role(Role.ROLE_CUSTOMER)
                .build();

        account1 = Account.builder()
                .id(10L)
                .accountNumber("1002345678")
                .ifscCode("OBIN0000101")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("100000.00"))
                .status(AccountStatus.ACTIVE)
                .user(customerUser1)
                .build();

        account2 = Account.builder()
                .id(11L)
                .accountNumber("2009876543")
                .ifscCode("OBIN0000101")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("50000.00"))
                .status(AccountStatus.ACTIVE)
                .user(customerUser2)
                .build();
    }

    @Test
    void testGetStatement_SuperAdminCanAccessAnyCustomerAccount() {
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdminUser));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(account1));

        Transaction tx1 = Transaction.builder()
                .id(101L)
                .transactionReference("TXN1001")
                .destinationAccount(account1)
                .amount(new BigDecimal("20000.00"))
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .description("Salary Credit")
                .timestamp(LocalDateTime.now().minusDays(5))
                .build();

        when(transactionRepository.findStatementTransactions(eq(account1), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(tx1));
        when(transactionRepository.findAllTransactionsFromDate(eq(account1), any(LocalDateTime.class)))
                .thenReturn(List.of(tx1));

        StatementResponse response = statementService.getStatement("1002345678", "THIS_MONTH", null, null, null, "ALL", "ALL", "DATE_DESC", "superadmin");

        assertNotNull(response);
        assertEquals("Rahul Sharma", response.getCustomerName());
        assertEquals("1002345678", response.getAccountNumber());
        assertEquals(new BigDecimal("80000.00"), response.getOpeningBalance());
        assertEquals(new BigDecimal("20000.00"), response.getTotalCredit());
        assertEquals(BigDecimal.ZERO, response.getTotalDebit());
        assertEquals(new BigDecimal("100000.00"), response.getClosingBalance());
        assertEquals(1, response.getTransactions().size());
    }

    @Test
    void testGetStatement_CustomerCanAccessOwnAccount() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(customerUser1));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(account1));
        when(transactionRepository.findStatementTransactions(eq(account1), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(transactionRepository.findAllTransactionsFromDate(eq(account1), any(LocalDateTime.class)))
                .thenReturn(List.of());

        StatementResponse response = statementService.getStatement("1002345678", "THIS_MONTH", null, null, null, "ALL", "ALL", "DATE_DESC", "rahul");

        assertNotNull(response);
        assertEquals("1002345678", response.getAccountNumber());
        assertEquals("Rahul Sharma", response.getCustomerName());
    }

    @Test
    void testGetStatement_CustomerCannotAccessOtherCustomerAccount_ThrowsAccessDenied() {
        when(userRepository.findByUsername("priya")).thenReturn(Optional.of(customerUser2));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(account1));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () ->
                statementService.getStatement("1002345678", "THIS_MONTH", null, null, null, "ALL", "ALL", "DATE_DESC", "priya"));

        assertTrue(exception.getMessage().contains("Unauthorized: You do not have permission"));
    }

    @Test
    void testGetStatement_CustomDateRangeValidation_FromDateAfterToDate_ThrowsException() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(customerUser1));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(account1));

        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().minusDays(5);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                statementService.getStatement("1002345678", "CUSTOM", from, to, null, "ALL", "ALL", "DATE_DESC", "rahul"));

        assertTrue(exception.getMessage().contains("cannot be after 'To Date'"));
    }

    @Test
    void testGetStatement_CustomDateRangeValidation_FutureDate_ThrowsException() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(customerUser1));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(account1));

        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusDays(5);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                statementService.getStatement("1002345678", "CUSTOM", from, to, null, "ALL", "ALL", "DATE_DESC", "rahul"));

        assertTrue(exception.getMessage().contains("Future dates are not allowed"));
    }

    @Test
    void testGeneratePdfAndExcelStatement() throws Exception {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(customerUser1));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(account1));
        when(transactionRepository.findStatementTransactions(eq(account1), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(transactionRepository.findAllTransactionsFromDate(eq(account1), any(LocalDateTime.class)))
                .thenReturn(List.of());

        StatementResponse response = statementService.getStatement("1002345678", "THIS_MONTH", null, null, null, "ALL", "ALL", "DATE_DESC", "rahul");

        byte[] pdfBytes = statementService.generatePdfStatement(response);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        byte[] excelBytes = statementService.generateExcelStatement(response);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }
}
