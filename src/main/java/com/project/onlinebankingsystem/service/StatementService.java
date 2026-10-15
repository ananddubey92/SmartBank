package com.project.onlinebankingsystem.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public StatementService(AccountRepository accountRepository,
                            TransactionRepository transactionRepository,
                            UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerOptionDTO> getCustomerOptionsForAdmin(String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        if (currentUser.getRole() != Role.ROLE_ADMIN && currentUser.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Only Admin and Super Admin can fetch customer list for statements.");
        }

        List<User> customers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ROLE_CUSTOMER || u.getRole() == Role.ROLE_ADMIN || u.getRole() == Role.ROLE_SUPER_ADMIN)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<CustomerOptionDTO> result = new ArrayList<>();
        for (User u : customers) {
            List<AccountOptionDTO> accounts = accountRepository.findByUserUsername(u.getUsername()).stream()
                    .map(acc -> AccountOptionDTO.builder()
                            .accountId(acc.getId())
                            .accountNumber(acc.getAccountNumber())
                            .accountType(acc.getAccountType().name())
                            .ifscCode(acc.getIfscCode())
                            .balance(acc.getBalance())
                            .status(acc.getStatus().name())
                            .build())
                    .toList();

            result.add(CustomerOptionDTO.builder()
                    .userId(u.getId())
                    .username(u.getUsername())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .role(u.getRole().name())
                    .accounts(accounts)
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public StatementResponse getStatement(String accountNumber, String preset, LocalDate fromDate, LocalDate toDate,
                                         String search, String typeFilter, String statusFilter, String sortOrder,
                                         String currentUsername) {
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        Account account;
        if (accountNumber != null && !accountNumber.trim().isEmpty()) {
            account = accountRepository.findByAccountNumber(accountNumber.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));
        } else {
            if (currentUser.getRole() == Role.ROLE_CUSTOMER) {
                List<Account> userAccounts = accountRepository.findByUserUsername(currentUsername);
                if (userAccounts.isEmpty()) {
                    throw new ResourceNotFoundException("No active account found for user: " + currentUsername);
                }
                account = userAccounts.get(0);
            } else {
                List<Account> allAccounts = accountRepository.findAll();
                if (allAccounts.isEmpty()) {
                    throw new ResourceNotFoundException("No accounts exist in system.");
                }
                account = allAccounts.get(0);
            }
        }

        // Security Check: Customer can ONLY access their own account
        if (currentUser.getRole() == Role.ROLE_CUSTOMER) {
            if (account.getUser() == null || !account.getUser().getUsername().equals(currentUsername)) {
                throw new AccessDeniedException("Unauthorized: You do not have permission to access statement for account " + account.getAccountNumber());
            }
        }

        // Calculate Date Range
        LocalDate now = LocalDate.now();
        String activePreset = (preset != null && !preset.trim().isEmpty()) ? preset.trim().toUpperCase() : "THIS_MONTH";
        LocalDate resolvedFromDate;
        LocalDate resolvedToDate;

        switch (activePreset) {
            case "TODAY":
                resolvedFromDate = now;
                resolvedToDate = now;
                break;
            case "LAST_7_DAYS":
                resolvedFromDate = now.minusDays(6);
                resolvedToDate = now;
                break;
            case "LAST_30_DAYS":
                resolvedFromDate = now.minusDays(29);
                resolvedToDate = now;
                break;
            case "LAST_MONTH":
                LocalDate firstOfLastMonth = now.minusMonths(1).withDayOfMonth(1);
                resolvedFromDate = firstOfLastMonth;
                resolvedToDate = firstOfLastMonth.withDayOfMonth(firstOfLastMonth.lengthOfMonth());
                break;
            case "THIS_YEAR":
                resolvedFromDate = now.withDayOfYear(1);
                resolvedToDate = now;
                break;
            case "CUSTOM":
                if (fromDate == null || toDate == null) {
                    throw new IllegalArgumentException("Both 'From Date' and 'To Date' are required for custom date range selection.");
                }
                resolvedFromDate = fromDate;
                resolvedToDate = toDate;
                break;
            case "THIS_MONTH":
            default:
                activePreset = "THIS_MONTH";
                resolvedFromDate = now.withDayOfMonth(1);
                resolvedToDate = now;
                break;
        }

        // Date Validation
        if (resolvedFromDate.isAfter(resolvedToDate)) {
            throw new IllegalArgumentException("Invalid date range: 'From Date' (" + resolvedFromDate + ") cannot be after 'To Date' (" + resolvedToDate + ").");
        }
        if (resolvedFromDate.isAfter(now) || resolvedToDate.isAfter(now)) {
            throw new IllegalArgumentException("Future dates are not allowed for transaction statements.");
        }

        LocalDateTime startDateTime = resolvedFromDate.atStartOfDay();
        LocalDateTime endDateTime = resolvedToDate.atTime(LocalTime.MAX);

        // Fetch DB transactions
        List<Transaction> dbTransactionsInPeriod = transactionRepository.findStatementTransactions(account, startDateTime, endDateTime);
        List<Transaction> dbTransactionsFromStartDate = transactionRepository.findAllTransactionsFromDate(account, startDateTime);

        // Calculate Opening Balance at startDateTime
        BigDecimal currentBalance = account.getBalance();
        BigDecimal netChangesFromStartToNow = BigDecimal.ZERO;

        for (Transaction tx : dbTransactionsFromStartDate) {
            boolean isCompleted = isTransactionCompleted(tx.getStatus());
            if (!isCompleted) continue;

            boolean isSource = tx.getSourceAccount() != null && tx.getSourceAccount().getId().equals(account.getId());
            boolean isDest = tx.getDestinationAccount() != null && tx.getDestinationAccount().getId().equals(account.getId());

            if (isSource) {
                netChangesFromStartToNow = netChangesFromStartToNow.subtract(tx.getAmount());
            } else if (isDest) {
                netChangesFromStartToNow = netChangesFromStartToNow.add(tx.getAmount());
            }
        }

        BigDecimal openingBalance = currentBalance.subtract(netChangesFromStartToNow);
        if (openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            openingBalance = BigDecimal.ZERO;
        }

        // Build Statement Transactions & Running Balances
        BigDecimal runningBalance = openingBalance;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalDebit = BigDecimal.ZERO;

        List<StatementTransactionDTO> allPeriodItems = new ArrayList<>();

        for (Transaction tx : dbTransactionsInPeriod) {
            boolean isSource = tx.getSourceAccount() != null && tx.getSourceAccount().getId().equals(account.getId());
            boolean isDest = tx.getDestinationAccount() != null && tx.getDestinationAccount().getId().equals(account.getId());
            boolean isCompleted = isTransactionCompleted(tx.getStatus());

            BigDecimal debit = null;
            BigDecimal credit = null;
            Boolean isDebit = isSource;
            String typeLabel;

            if (isSource) {
                debit = tx.getAmount();
                if (!isCompleted) {
                    typeLabel = "Failed/Rejected Transaction (" + formatStatusLabel(tx.getStatus()) + ")";
                } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                    typeLabel = "Cash Withdrawal";
                } else if (tx.getType() == TransactionType.UPI_TRANSFER || tx.getType() == TransactionType.UPI_QR_PAYMENT || tx.getType() == TransactionType.UPI_REQUEST_PAYMENT) {
                    typeLabel = "UPI Payment";
                } else {
                    typeLabel = "Fund Transfer (Debit)";
                }

                if (isCompleted) {
                    totalDebit = totalDebit.add(tx.getAmount());
                }
            } else {
                credit = tx.getAmount();
                if (!isCompleted) {
                    typeLabel = "Failed/Rejected Transaction (" + formatStatusLabel(tx.getStatus()) + ")";
                } else if (tx.getType() == TransactionType.DEPOSIT) {
                    typeLabel = "Cash Deposit";
                } else if (tx.getType() == TransactionType.UPI_TRANSFER || tx.getType() == TransactionType.UPI_QR_PAYMENT || tx.getType() == TransactionType.UPI_REQUEST_PAYMENT) {
                    typeLabel = "UPI Received";
                } else {
                    typeLabel = "Received Payment (Credit)";
                }

                if (isCompleted) {
                    totalCredit = totalCredit.add(tx.getAmount());
                }
            }

            BigDecimal previousBalance = runningBalance;
            if (isCompleted) {
                if (isSource) {
                    runningBalance = runningBalance.subtract(tx.getAmount());
                } else {
                    runningBalance = runningBalance.add(tx.getAmount());
                }
            }
            BigDecimal updatedBalance = runningBalance;

            ExpenseCategory cat = tx.getCategory() != null ? tx.getCategory() : ExpenseCategory.OTHER;

            StatementTransactionDTO dto = StatementTransactionDTO.builder()
                    .transactionId(tx.getId())
                    .referenceNumber(tx.getTransactionReference())
                    .timestamp(tx.getTimestamp())
                    .transactionType(typeLabel)
                    .rawType(tx.getType() != null ? tx.getType().name() : "TRANSFER")
                    .description(tx.getDescription() != null ? tx.getDescription() : typeLabel)
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .transactionAmount(tx.getAmount())
                    .previousBalance(previousBalance)
                    .updatedBalance(updatedBalance)
                    .status(tx.getStatus() != null ? tx.getStatus().name() : "COMPLETED")
                    .isDebit(isDebit)
                    .sourceAccountNumber(tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : null)
                    .destinationAccountNumber(tx.getDestinationAccount() != null ? tx.getDestinationAccount().getAccountNumber() : null)
                    .categoryDisplayName(cat.getDisplayName())
                    .categoryIcon(cat.getIcon())
                    .categoryColor(cat.getColor())
                    .build();

            allPeriodItems.add(dto);
        }

        BigDecimal closingBalance = openingBalance.add(totalCredit).subtract(totalDebit);

        // Apply In-Memory Filtering (search, type, status, sort)
        List<StatementTransactionDTO> filtered = new ArrayList<>(allPeriodItems);

        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toLowerCase();
            filtered = filtered.stream()
                    .filter(t -> (t.getReferenceNumber() != null && t.getReferenceNumber().toLowerCase().contains(q))
                            || (t.getTransactionId() != null && t.getTransactionId().toString().contains(q))
                            || (t.getDescription() != null && t.getDescription().toLowerCase().contains(q))
                            || (t.getTransactionType() != null && t.getTransactionType().toLowerCase().contains(q)))
                    .toList();
        }

        if (typeFilter != null && !typeFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(typeFilter)) {
            String tf = typeFilter.trim().toUpperCase();
            filtered = filtered.stream().filter(t -> {
                if ("DEPOSIT".equals(tf)) return !t.getIsDebit() && "DEPOSIT".equals(t.getRawType());
                if ("WITHDRAWAL".equals(tf)) return t.getIsDebit() && "WITHDRAWAL".equals(t.getRawType());
                if ("TRANSFER".equals(tf)) return "TRANSFER".equals(t.getRawType()) || t.getRawType().startsWith("UPI");
                if ("RECEIVED".equals(tf)) return !t.getIsDebit();
                if ("PAYMENT".equals(tf)) return t.getIsDebit();
                if ("FAILED".equals(tf)) return !isTransactionCompleted(TransactionStatus.valueOf(t.getStatus()));
                return true;
            }).toList();
        }

        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            String sf = statusFilter.trim().toUpperCase();
            filtered = filtered.stream().filter(t -> {
                if ("SUCCESS".equals(sf) || "COMPLETED".equals(sf)) return isTransactionCompleted(TransactionStatus.valueOf(t.getStatus()));
                return t.getStatus().equalsIgnoreCase(sf);
            }).toList();
        }

        // Sort
        String sort = (sortOrder != null && !sortOrder.trim().isEmpty()) ? sortOrder.trim().toUpperCase() : "DATE_DESC";
        filtered = new ArrayList<>(filtered);
        filtered.sort((a, b) -> {
            switch (sort) {
                case "DATE_ASC":
                    return a.getTimestamp().compareTo(b.getTimestamp());
                case "AMOUNT_DESC":
                    return b.getTransactionAmount().compareTo(a.getTransactionAmount());
                case "AMOUNT_ASC":
                    return a.getTransactionAmount().compareTo(b.getTransactionAmount());
                case "DATE_DESC":
                default:
                    return b.getTimestamp().compareTo(a.getTimestamp());
            }
        });

        User customer = account.getUser();
        String message = filtered.isEmpty() ? "No transactions found for the selected customer and date range." : null;

        return StatementResponse.builder()
                .userId(customer != null ? customer.getId() : null)
                .customerName(customer != null ? customer.getFullName() : account.getAccountHolderName())
                .username(customer != null ? customer.getUsername() : null)
                .email(customer != null ? customer.getEmail() : "N/A")
                .phone(customer != null ? customer.getPhone() : "N/A")
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType().name())
                .ifscCode(account.getIfscCode())
                .currentBalance(account.getBalance())
                .preset(activePreset)
                .fromDate(resolvedFromDate)
                .toDate(resolvedToDate)
                .openingBalance(openingBalance)
                .totalCredit(totalCredit)
                .totalDebit(totalDebit)
                .closingBalance(closingBalance)
                .totalTransactions(filtered.size())
                .message(message)
                .transactions(filtered)
                .build();
    }

    public byte[] generatePdfStatement(StatementResponse statement) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(document, out);

        document.open();

        Color primaryNavy = new Color(15, 23, 42);
        Color lightBg = new Color(248, 250, 252);
        Color borderGray = new Color(226, 232, 240);
        Color darkText = new Color(30, 41, 59);
        Color grayText = new Color(100, 116, 139);
        Color creditGreen = new Color(22, 163, 74);
        Color debitRed = new Color(220, 38, 38);

        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, primaryNavy);
        Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA, 10, grayText);
        Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, primaryNavy);
        Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, darkText);
        Font fontRegular = FontFactory.getFont(FontFactory.HELVETICA, 9, darkText);
        Font fontSmall = FontFactory.getFont(FontFactory.HELVETICA, 8, grayText);
        Font fontTableHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font fontCredit = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, creditGreen);
        Font fontDebit = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, debitRed);

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        Paragraph title = new Paragraph("SmartBank AI", fontTitle);
        Paragraph tagline = new Paragraph("Official Bank Statement / Account Ledger", fontSubtitle);
        logoCell.addElement(title);
        logoCell.addElement(tagline);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph genDate = new Paragraph("Generated On: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), fontSmall);
        Paragraph bankIfsc = new Paragraph("IFSC Code: " + statement.getIfscCode(), fontSmall);
        genDate.setAlignment(Element.ALIGN_RIGHT);
        bankIfsc.setAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(genDate);
        metaCell.addElement(bankIfsc);

        headerTable.addCell(logoCell);
        headerTable.addCell(metaCell);
        document.add(headerTable);

        document.add(new Paragraph(" "));

        PdfPTable infoBox = new PdfPTable(4);
        infoBox.setWidthPercentage(100);
        infoBox.setWidths(new float[]{25, 25, 25, 25});

        addInfoCell(infoBox, "Customer Name:", statement.getCustomerName(), fontSmall, fontBold, lightBg, borderGray);
        addInfoCell(infoBox, "Account Number:", statement.getAccountNumber(), fontSmall, fontBold, lightBg, borderGray);
        addInfoCell(infoBox, "Account Type:", statement.getAccountType(), fontSmall, fontBold, lightBg, borderGray);
        addInfoCell(infoBox, "Statement Period:", statement.getFromDate() + " to " + statement.getToDate(), fontSmall, fontBold, lightBg, borderGray);

        document.add(infoBox);
        document.add(new Paragraph(" "));

        PdfPTable summaryTable = new PdfPTable(4);
        summaryTable.setWidthPercentage(100);
        summaryTable.setWidths(new float[]{25, 25, 25, 25});

        addSummaryCell(summaryTable, "OPENING BALANCE", "₹ " + formatAmount(statement.getOpeningBalance()), fontSmall, fontHeader, lightBg, borderGray, darkText);
        addSummaryCell(summaryTable, "TOTAL CREDIT (+)", "₹ " + formatAmount(statement.getTotalCredit()), fontSmall, fontHeader, new Color(240, 253, 244), borderGray, creditGreen);
        addSummaryCell(summaryTable, "TOTAL DEBIT (-)", "₹ " + formatAmount(statement.getTotalDebit()), fontSmall, fontHeader, new Color(254, 242, 242), borderGray, debitRed);
        addSummaryCell(summaryTable, "CLOSING BALANCE", "₹ " + formatAmount(statement.getClosingBalance()), fontSmall, fontHeader, lightBg, borderGray, primaryNavy);

        document.add(summaryTable);
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{14, 12, 14, 20, 10, 10, 10, 10});

        String[] headers = {"Tx Ref", "Date", "Type", "Description", "Debit (₹)", "Credit (₹)", "Balance (₹)", "Status"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontTableHead));
            cell.setBackgroundColor(primaryNavy);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (StatementTransactionDTO tx : statement.getTransactions()) {
            boolean isDebit = Boolean.TRUE.equals(tx.getIsDebit());

            addTableCell(table, tx.getReferenceNumber(), fontSmall, Element.ALIGN_LEFT);
            addTableCell(table, tx.getTimestamp() != null ? tx.getTimestamp().format(dtf) : "-", fontSmall, Element.ALIGN_CENTER);
            addTableCell(table, tx.getTransactionType(), fontSmall, Element.ALIGN_LEFT);
            addTableCell(table, tx.getDescription(), fontSmall, Element.ALIGN_LEFT);

            addTableCell(table, tx.getDebitAmount() != null ? formatAmount(tx.getDebitAmount()) : "-", isDebit ? fontDebit : fontSmall, Element.ALIGN_RIGHT);
            addTableCell(table, tx.getCreditAmount() != null ? formatAmount(tx.getCreditAmount()) : "-", !isDebit ? fontCredit : fontSmall, Element.ALIGN_RIGHT);
            addTableCell(table, formatAmount(tx.getUpdatedBalance()), fontBold, Element.ALIGN_RIGHT);
            addTableCell(table, formatStatusLabel(TransactionStatus.valueOf(tx.getStatus())), fontSmall, Element.ALIGN_CENTER);
        }

        if (statement.getTransactions().isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No transactions found for the selected customer and date range.", fontRegular));
            emptyCell.setColspan(8);
            emptyCell.setPadding(15);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        }

        document.add(table);

        Paragraph footerNote = new Paragraph("\nThis is a computer-generated bank transaction statement and does not require a physical signature.", fontSmall);
        footerNote.setAlignment(Element.ALIGN_CENTER);
        document.add(footerNote);

        document.close();
        return out.toByteArray();
    }

    public byte[] generateExcelStatement(StatementResponse statement) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Bank Statement");

        org.apache.poi.ss.usermodel.Font fontHeader = workbook.createFont();
        fontHeader.setBold(true);
        fontHeader.setFontHeightInPoints((short) 10);
        fontHeader.setColor(IndexedColors.WHITE.getIndex());

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(fontHeader);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle boldStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font fBold = workbook.createFont();
        fBold.setBold(true);
        boldStyle.setFont(fBold);

        int rowIdx = 0;

        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("SmartBank AI - Person-wise Bank Statement");
        titleCell.setCellStyle(boldStyle);

        rowIdx++;

        sheet.createRow(rowIdx++).createCell(0).setCellValue("Customer Name: " + statement.getCustomerName());
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Account Number: " + statement.getAccountNumber());
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Statement Period: " + statement.getFromDate() + " to " + statement.getToDate());
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Generated On: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        rowIdx++;

        sheet.createRow(rowIdx++).createCell(0).setCellValue("Opening Balance: ₹" + formatAmount(statement.getOpeningBalance()));
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Total Credit: ₹" + formatAmount(statement.getTotalCredit()));
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Total Debit: ₹" + formatAmount(statement.getTotalDebit()));
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Closing Balance: ₹" + formatAmount(statement.getClosingBalance()));

        rowIdx++;

        Row tableHeaderRow = sheet.createRow(rowIdx++);
        String[] cols = {"Tx ID", "Reference Number", "Date & Time", "Transaction Type", "Description", "Debit (₹)", "Credit (₹)", "Previous Balance (₹)", "Updated Balance (₹)", "Status"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = tableHeaderRow.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (StatementTransactionDTO tx : statement.getTransactions()) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(tx.getTransactionId());
            r.createCell(1).setCellValue(tx.getReferenceNumber());
            r.createCell(2).setCellValue(tx.getTimestamp() != null ? tx.getTimestamp().format(dtf) : "");
            r.createCell(3).setCellValue(tx.getTransactionType());
            r.createCell(4).setCellValue(tx.getDescription());
            r.createCell(5).setCellValue(tx.getDebitAmount() != null ? tx.getDebitAmount().doubleValue() : 0.0);
            r.createCell(6).setCellValue(tx.getCreditAmount() != null ? tx.getCreditAmount().doubleValue() : 0.0);
            r.createCell(7).setCellValue(tx.getPreviousBalance() != null ? tx.getPreviousBalance().doubleValue() : 0.0);
            r.createCell(8).setCellValue(tx.getUpdatedBalance() != null ? tx.getUpdatedBalance().doubleValue() : 0.0);
            r.createCell(9).setCellValue(tx.getStatus());
        }

        for (int i = 0; i < cols.length; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    private boolean isTransactionCompleted(TransactionStatus status) {
        if (status == null) return true;
        return status == TransactionStatus.COMPLETED || status == TransactionStatus.SUCCESS;
    }

    private String formatStatusLabel(TransactionStatus status) {
        if (status == null) return "Completed";
        switch (status) {
            case COMPLETED:
            case SUCCESS:
                return "Completed";
            case BLOCKED:
                return "Blocked";
            case FAILED:
                return "Failed";
            case UNDER_REVIEW:
                return "Under Review";
            case PENDING:
                return "Pending";
            default:
                return status.name();
        }
    }

    private void addInfoCell(PdfPTable table, String label, String val, Font labelFont, Font valFont, Color bg, Color border) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(6);

        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(val != null ? val : "-", valFont));
        table.addCell(cell);
    }

    private void addSummaryCell(PdfPTable table, String title, String val, Font labelFont, Font valFont, Color bg, Color border, Color textColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph p1 = new Paragraph(title, labelFont);
        p1.setAlignment(Element.ALIGN_CENTER);
        Font vFont = new Font(valFont);
        vFont.setColor(textColor);
        Paragraph p2 = new Paragraph(val, vFont);
        p2.setAlignment(Element.ALIGN_CENTER);

        cell.addElement(p1);
        cell.addElement(p2);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private String formatAmount(BigDecimal amt) {
        if (amt == null) return "0.00";
        return String.format("%,.2f", amt);
    }
}
