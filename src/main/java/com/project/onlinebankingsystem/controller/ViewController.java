package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.config.JwtUtils;
import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.exception.InsufficientBalanceException;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.AuditLog;
import com.project.onlinebankingsystem.model.Beneficiary;
import com.project.onlinebankingsystem.model.LoginActivity;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import com.project.onlinebankingsystem.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ViewController {

    private final UserService userService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final BeneficiaryService beneficiaryService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final JwtUtils jwtUtils;
    private final ExpenseAnalyticsService expenseAnalyticsService;
    private final FraudDetectionService fraudDetectionService;
    private final FinancialHealthService financialHealthService;
    private final NotificationService notificationService;
    private final AiBankingAssistantService aiBankingAssistantService;
    private final SuperAdminService superAdminService;
    private final UpiService upiService;
    private final StatementService statementService;
    private final DebitCardService debitCardService;

    public ViewController(UserService userService,
                          AccountService accountService,
                          TransactionService transactionService,
                          BeneficiaryService beneficiaryService,
                          UserRepository userRepository,
                          AccountRepository accountRepository,
                          JwtUtils jwtUtils,
                          ExpenseAnalyticsService expenseAnalyticsService,
                          FraudDetectionService fraudDetectionService,
                          FinancialHealthService financialHealthService,
                          NotificationService notificationService,
                          AiBankingAssistantService aiBankingAssistantService,
                          SuperAdminService superAdminService,
                          UpiService upiService,
                          StatementService statementService,
                          DebitCardService debitCardService) {
        this.userService = userService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.beneficiaryService = beneficiaryService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.jwtUtils = jwtUtils;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.fraudDetectionService = fraudDetectionService;
        this.financialHealthService = financialHealthService;
        this.notificationService = notificationService;
        this.aiBankingAssistantService = aiBankingAssistantService;
        this.superAdminService = superAdminService;
        this.upiService = upiService;
        this.statementService = statementService;
        this.debitCardService = debitCardService;
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private boolean isSuperAdminAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> Role.ROLE_SUPER_ADMIN.name().equals(a.getAuthority()));
    }

    private boolean isAdminAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()));
    }

    @ModelAttribute("unreadNotificationCount")
    public long populateUnreadNotificationCount(Principal principal) {
        if (principal != null) {
            try {
                return notificationService.getUnreadCount(principal.getName());
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    @GetMapping("/")
    public String index() {
        if (isAuthenticated()) {
            if (isSuperAdminAuthenticated()) {
                return "redirect:/super-admin";
            }
            if (isAdminAuthenticated()) {
                return "redirect:/admin";
            }
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        if (isAuthenticated()) {
            if (isSuperAdminAuthenticated()) {
                return "redirect:/super-admin";
            }
            if (isAdminAuthenticated()) {
                return "redirect:/admin";
            }
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        if (isAuthenticated()) {
            if (isSuperAdminAuthenticated()) {
                return "redirect:/super-admin";
            }
            if (isAdminAuthenticated()) {
                return "redirect:/admin";
            }
            return "redirect:/dashboard";
        }
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
                               BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.registerUser(registerRequest);
            redirectAttributes.addFlashAttribute("success", "Registration successful! Please login.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, HttpServletRequest request, Model model) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return "redirect:/login?error";
        }

        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return "redirect:/super-admin";
        }
        if (user.getRole() == Role.ROLE_ADMIN) {
            return "redirect:/admin";
        }

        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        List<TransactionResponse> transactions = new ArrayList<>();
        if (!accounts.isEmpty()) {
            transactions = transactionService.getTransactionHistory(accounts.get(0).getAccountNumber());
        }

        ExpenseAnalyticsResponse analytics = expenseAnalyticsService.getAnalyticsForUser(principal.getName());
        FinancialHealthResponse health = financialHealthService.calculateFinancialHealth(principal.getName());
        int securityScore = userService.calculateSecurityScore(principal.getName());
        long unreadNotifications = notificationService.getUnreadCount(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("transactions", transactions);
        model.addAttribute("analytics", analytics);
        model.addAttribute("health", health);
        model.addAttribute("securityScore", securityScore);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("beneficiaries", beneficiaryService.getBeneficiariesByUsername(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        model.addAttribute("debitCard", debitCardService.getCardForCustomer(principal.getName()).orElse(null));
        model.addAttribute("debitCards", debitCardService.getCardsForCustomer(principal.getName()));

        return "dashboard";
    }

    @GetMapping({"/debit-card", "/my-card"})
    public String debitCardView(Principal principal, HttpServletRequest request, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return "redirect:/login?error";
        }

        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return "redirect:/super-admin";
        }
        if (user.getRole() == Role.ROLE_ADMIN) {
            return "redirect:/admin";
        }

        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        DebitCardResponse debitCard = debitCardService.getCardForCustomer(principal.getName()).orElse(null);
        List<DebitCardResponse> debitCards = debitCardService.getCardsForCustomer(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("debitCard", debitCard);
        model.addAttribute("debitCards", debitCards);
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "debit-card";
    }

    @GetMapping("/upi")
    public String upiDashboard(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.getName()));

        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        UpiProfileResponse upiProfile = null;
        try {
            upiProfile = upiService.getUpiProfile(principal.getName());
        } catch (Exception ignored) {}

        UpiLimitsResponse limits = upiService.getUpiLimits(principal.getName());
        List<UpiTransactionResponse> upiTransactions = upiService.getUpiTransactions(principal.getName(), "ALL", "ALL", null);
        List<UpiMoneyRequestDTO> receivedRequests = upiService.getReceivedRequests(principal.getName());
        List<UpiMoneyRequestDTO> sentRequests = upiService.getSentRequests(principal.getName());
        UpiAnalyticsResponse upiAnalytics = upiService.getUpiAnalytics(principal.getName());
        UpiQrResponse qrInfo = null;
        try {
            qrInfo = upiService.generatePersonalQr(principal.getName());
        } catch (Exception ignored) {}

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("upiProfile", upiProfile);
        model.addAttribute("limits", limits);
        model.addAttribute("transactions", upiTransactions);
        model.addAttribute("receivedRequests", receivedRequests);
        model.addAttribute("sentRequests", sentRequests);
        model.addAttribute("analytics", upiAnalytics);
        model.addAttribute("qrInfo", qrInfo);
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "upi";
    }

    @GetMapping("/financial-health")
    public String financialHealth(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        FinancialHealthResponse health = financialHealthService.calculateFinancialHealth(principal.getName());
        long unreadNotifications = notificationService.getUnreadCount(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("health", health);
        model.addAttribute("prediction", health.getSpendingPrediction());
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "financial-health";
    }

    @GetMapping("/ai-assistant")
    public String aiAssistant(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        long unreadNotifications = notificationService.getUnreadCount(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "ai-assistant";
    }

    @GetMapping("/notifications")
    public String notifications(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<NotificationResponse> notifications = notificationService.getNotificationsForUser(principal.getName());
        long unreadNotifications = notificationService.getUnreadCount(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("unreadCount", unreadNotifications);
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "notifications";
    }

    @GetMapping("/account-details")
    public String accountDetails(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        return "account-details";
    }

    @GetMapping("/deposit")
    public String depositForm(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        return "deposit";
    }

    @GetMapping("/withdraw")
    public String withdrawForm(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        return "withdraw";
    }

    @GetMapping("/transfer")
    public String transferForm(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        List<Beneficiary> beneficiaries = beneficiaryService.getBeneficiariesByUsername(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("beneficiaries", beneficiaries);
        model.addAttribute("categories", ExpenseCategory.values());
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        return "transfer";
    }

    @GetMapping("/beneficiaries")
    public String beneficiariesForm(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        List<Beneficiary> beneficiaries = beneficiaryService.getBeneficiariesByUsername(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("beneficiaries", beneficiaries);
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        return "beneficiaries";
    }

    @GetMapping({"/accounts/create", "/account/create", "/accounts/open", "/account/open", "/open-account", "/create-account"})
    public String handleAccountCreateDirectGet(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null || user.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Access Denied: Only Super Admin can open a new bank account.");
        }
        return "redirect:/super-admin#section-accounts";
    }

    @PostMapping("/accounts/create")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public String createAccount(Principal principal, @RequestParam String accountType, RedirectAttributes redirectAttributes) {
        try {
            AccountRequest request = new AccountRequest(accountType);
            accountService.createAccount(principal.getName(), request);
            redirectAttributes.addFlashAttribute("success", "New account created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/super-admin";
    }

    @PostMapping("/transactions/deposit")
    public String deposit(@RequestParam String accountNumber, @RequestParam BigDecimal amount, RedirectAttributes redirectAttributes) {
        try {
            transactionService.deposit(accountNumber, amount, "ATM Cash Deposit");
            redirectAttributes.addFlashAttribute("success", "Cash deposited successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transactions/withdraw")
    public String withdraw(@RequestParam String accountNumber, @RequestParam BigDecimal amount, RedirectAttributes redirectAttributes) {
        try {
            transactionService.withdraw(accountNumber, amount, "ATM Cash Withdrawal");
            redirectAttributes.addFlashAttribute("success", "Cash withdrawn successfully!");
        } catch (InsufficientBalanceException e) {
            redirectAttributes.addFlashAttribute("error", "Insufficient balance to withdraw!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transactions/transfer")
    public String transfer(@RequestParam String sourceAccountNumber,
                           @RequestParam String destinationAccountNumber,
                           @RequestParam BigDecimal amount,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String otp,
                           @RequestParam(required = false) String pin,
                           RedirectAttributes redirectAttributes) {
        try {
            transactionService.transfer(sourceAccountNumber, destinationAccountNumber, amount, description, category, otp, pin);
            redirectAttributes.addFlashAttribute("success", "Fund transfer completed successfully!");
        } catch (InsufficientBalanceException e) {
            redirectAttributes.addFlashAttribute("error", "Insufficient balance to transfer!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "Transfer failed.");
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/beneficiaries/create")
    public String addBeneficiary(Principal principal, @RequestParam String name, @RequestParam String accountNumber,
                                 @RequestParam String ifscCode, @RequestParam(required = false) String bankName,
                                 RedirectAttributes redirectAttributes) {
        try {
            BeneficiaryRequest request = new BeneficiaryRequest(name, accountNumber, ifscCode, bankName);
            beneficiaryService.addBeneficiary(principal.getName(), request);
            redirectAttributes.addFlashAttribute("success", "Beneficiary added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/beneficiaries";
    }

    @PostMapping("/beneficiaries/delete/{id}")
    public String deleteBeneficiary(Principal principal, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            beneficiaryService.deleteBeneficiary(id, principal.getName());
            redirectAttributes.addFlashAttribute("success", "Beneficiary deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/beneficiaries";
    }

    @GetMapping("/super-admin")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public String superAdminPanel(Principal principal, Model model) {
        SuperAdminDashboardDTO stats = superAdminService.getDashboardMetrics();
        List<UserResponseDTO> admins = superAdminService.getAllAdmins();
        List<UserResponseDTO> customers = superAdminService.getAllCustomers();
        List<AccountResponse> accounts = superAdminService.getAllAccounts();
        List<TransactionResponse> transactions = superAdminService.getAllTransactions();
        List<TransactionResponse> suspicious = superAdminService.getSuspiciousTransactions();
        List<AuditLog> auditLogs = stats.getRecentAuditLogs();

        model.addAttribute("stats", stats);
        model.addAttribute("admins", admins);
        model.addAttribute("customers", customers);
        model.addAttribute("accounts", accounts);
        model.addAttribute("transactions", transactions);
        model.addAttribute("suspiciousTransactions", suspicious);
        model.addAttribute("auditLogs", auditLogs);
        model.addAttribute("debitCards", superAdminService.getAllDebitCards());

        if (principal != null) {
            userRepository.findByUsername(principal.getName()).ifPresent(u -> model.addAttribute("user", u));
            model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        }

        return "super-admin";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String adminPanel(Principal principal, Model model) {
        List<User> users = userService.getAllUsers();
        List<AccountResponse> accounts = accountService.getAllAccounts();
        List<TransactionResponse> transactions = transactionService.getAllTransactions();

        long blockedCount = transactions.stream().filter(t -> "BLOCKED".equalsIgnoreCase(t.getStatus())).count();
        long underReviewCount = transactions.stream().filter(t -> "UNDER_REVIEW".equalsIgnoreCase(t.getStatus())).count();
        long fraudDetected = transactions.stream().filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())).count();
        long safeCount = transactions.stream().filter(t -> "LOW".equalsIgnoreCase(t.getRiskLevel()) || "MEDIUM".equalsIgnoreCase(t.getRiskLevel())).count();

        List<TransactionResponse> suspicious = transactions.stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())
                        || "BLOCKED".equalsIgnoreCase(t.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(t.getStatus()))
                .toList();

        BigDecimal totalLiquidity = accounts.stream()
                .map(AccountResponse::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("users", users);
        model.addAttribute("accounts", accounts);
        model.addAttribute("transactions", transactions);
        model.addAttribute("totalLiquidity", totalLiquidity);
        model.addAttribute("blockedCount", blockedCount);
        model.addAttribute("underReviewCount", underReviewCount);
        model.addAttribute("fraudDetected", fraudDetected);
        model.addAttribute("safeCount", safeCount);
        model.addAttribute("suspiciousTransactions", suspicious);

        if (principal != null) {
            userRepository.findByUsername(principal.getName()).ifPresent(u -> model.addAttribute("user", u));
            model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
        }

        return "admin";
    }

    @PostMapping("/admin/accounts/update-status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String updateAccountStatus(@RequestParam String accountNumber, @RequestParam String status, RedirectAttributes redirectAttributes) {
        try {
            Account account = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            account.setStatus(AccountStatus.valueOf(status.toUpperCase()));
            accountRepository.save(account);
            redirectAttributes.addFlashAttribute("success", "Account status updated to " + status + "!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @GetMapping("/favicon.ico")
    @ResponseBody
    public void returnNoFavicon() {
    }

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        ProfileUpdateRequest profileRequest = ProfileUpdateRequest.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fatherName(user.getFatherName())
                .motherName(user.getMotherName())
                .dob(user.getDob())
                .gender(user.getGender() != null ? user.getGender().name() : "MALE")
                .address(user.getAddress())
                .build();
        model.addAttribute("profileRequest", profileRequest);

        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(Principal principal, @Valid @ModelAttribute("profileRequest") ProfileUpdateRequest profileRequest,
                                BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            User user = userRepository.findByUsername(principal.getName()).orElse(null);
            List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
            model.addAttribute("user", user);
            model.addAttribute("accounts", accounts);
            model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));
            return "profile";
        }
        try {
            userService.updateProfile(principal.getName(), profileRequest);
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    @GetMapping("/transactions")
    public String transactionHistory(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());

        List<TransactionResponse> transactions = new ArrayList<>();
        for (AccountResponse acc : accounts) {
            transactions.addAll(transactionService.getTransactionHistory(acc.getAccountNumber()));
        }
        transactions.sort((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()));

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("transactions", transactions);
        model.addAttribute("categories", ExpenseCategory.values());
        model.addAttribute("riskLevels", RiskLevel.values());
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "transactions";
    }

    @GetMapping("/analytics")
    public String expenseAnalytics(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        ExpenseAnalyticsResponse analytics = expenseAnalyticsService.getAnalyticsForUser(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("analytics", analytics);
        model.addAttribute("categories", ExpenseCategory.values());
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "analytics";
    }

    @GetMapping("/fraud-detection")
    public String fraudDetection(@RequestParam(name = "amount", required = false) BigDecimal amount,
                                 @RequestParam(name = "transferAmount", required = false) BigDecimal transferAmount,
                                 Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        List<Beneficiary> beneficiaries = beneficiaryService.getBeneficiariesByUsername(principal.getName());

        List<TransactionResponse> userTransactions = new ArrayList<>();
        for (AccountResponse acc : accounts) {
            userTransactions.addAll(transactionService.getTransactionHistory(acc.getAccountNumber()));
        }

        List<TransactionResponse> allTransactions = transactionService.getAllTransactions();
        long totalEvaluated = allTransactions.size();
        long lowCount = allTransactions.stream().filter(t -> "LOW".equalsIgnoreCase(t.getRiskLevel())).count();
        long medCount = allTransactions.stream().filter(t -> "MEDIUM".equalsIgnoreCase(t.getRiskLevel())).count();
        long highCount = allTransactions.stream().filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())).count();
        long blockedCount = allTransactions.stream().filter(t -> "BLOCKED".equalsIgnoreCase(t.getStatus())).count();

        List<TransactionResponse> flaggedTransactions = userTransactions.stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())
                        || "BLOCKED".equalsIgnoreCase(t.getStatus()))
                .toList();

        BigDecimal resolvedAmount = amount != null ? amount : transferAmount;
        if (resolvedAmount != null && resolvedAmount.compareTo(BigDecimal.ZERO) > 0) {
            model.addAttribute("simAmount", resolvedAmount);
        } else {
            model.addAttribute("simAmount", new BigDecimal("5000"));
        }

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("beneficiaries", beneficiaries);
        model.addAttribute("flaggedTransactions", flaggedTransactions);
        model.addAttribute("totalEvaluated", totalEvaluated);
        model.addAttribute("lowRiskCount", lowCount);
        model.addAttribute("mediumRiskCount", medCount);
        model.addAttribute("highRiskCount", highCount);
        model.addAttribute("blockedCount", blockedCount);
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "fraud-detection";
    }

    @GetMapping("/security")
    public String securityDashboard(Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AccountResponse> accounts = accountService.getAccountsByUsername(principal.getName());
        List<LoginActivity> loginActivities = userService.getRecentLoginActivities(principal.getName());

        List<TransactionResponse> userTransactions = new ArrayList<>();
        for (AccountResponse acc : accounts) {
            userTransactions.addAll(transactionService.getTransactionHistory(acc.getAccountNumber()));
        }
        List<TransactionResponse> suspiciousTransactions = userTransactions.stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())
                        || "BLOCKED".equalsIgnoreCase(t.getStatus()))
                .toList();

        int securityScore = userService.calculateSecurityScore(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("loginActivities", loginActivities);
        model.addAttribute("suspiciousTransactions", suspiciousTransactions);
        model.addAttribute("securityScore", securityScore);
        model.addAttribute("hasPin", user.getTransactionPin() != null && !user.getTransactionPin().isBlank());
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "security";
    }

    @PostMapping("/security/change-password")
    public String changePassword(Principal principal,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.changePassword(principal.getName(), currentPassword, newPassword, confirmPassword);
            redirectAttributes.addFlashAttribute("success", "Password updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/security";
    }

    @PostMapping("/security/update-pin")
    public String updateTransactionPin(Principal principal,
                                       @RequestParam String pin,
                                       @RequestParam String password,
                                       RedirectAttributes redirectAttributes) {
        try {
            userService.updateTransactionPin(principal.getName(), pin, password);
            redirectAttributes.addFlashAttribute("success", "Transaction Security PIN updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/security";
    }

    @GetMapping("/statement")
    public String statementPage(@RequestParam(name = "accountNumber", required = false) String accountNumber,
                                @RequestParam(name = "preset", required = false, defaultValue = "THIS_MONTH") String preset,
                                @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                @RequestParam(name = "search", required = false) String search,
                                @RequestParam(name = "type", required = false) String type,
                                @RequestParam(name = "status", required = false) String status,
                                @RequestParam(name = "sort", required = false) String sort,
                                Principal principal, Model model) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.getName()));

        boolean isStaff = user.getRole() == Role.ROLE_ADMIN || user.getRole() == Role.ROLE_SUPER_ADMIN;
        List<CustomerOptionDTO> customerOptions = new ArrayList<>();
        if (isStaff) {
            customerOptions = statementService.getCustomerOptionsForAdmin(principal.getName());
        }

        StatementResponse statement = null;
        String errorMessage = null;

        try {
            statement = statementService.getStatement(accountNumber, preset, fromDate, toDate, search, type, status, sort, principal.getName());
        } catch (IllegalArgumentException e) {
            errorMessage = e.getMessage();
            // Fallback to default range if user provided invalid custom range
            try {
                statement = statementService.getStatement(accountNumber, "THIS_MONTH", null, null, search, type, status, sort, principal.getName());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }

        List<AccountResponse> userAccounts = accountService.getAccountsByUsername(principal.getName());

        model.addAttribute("user", user);
        model.addAttribute("isStaff", isStaff);
        model.addAttribute("customers", customerOptions);
        model.addAttribute("userAccounts", userAccounts);
        model.addAttribute("statement", statement);
        model.addAttribute("selectedAccountNumber", statement != null ? statement.getAccountNumber() : accountNumber);
        model.addAttribute("selectedPreset", preset);
        model.addAttribute("fromDate", fromDate != null ? fromDate : (statement != null ? statement.getFromDate() : null));
        model.addAttribute("toDate", toDate != null ? toDate : (statement != null ? statement.getToDate() : null));
        model.addAttribute("searchQuery", search);
        model.addAttribute("typeFilter", type);
        model.addAttribute("statusFilter", status);
        model.addAttribute("sortOrder", sort);
        if (errorMessage != null) {
            model.addAttribute("error", errorMessage);
        }
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(principal.getName()));
        model.addAttribute("sessionToken", jwtUtils.generateToken(principal.getName()));

        return "statement";
    }
}
