package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.CustomerOptionDTO;
import com.project.onlinebankingsystem.dto.StatementResponse;
import com.project.onlinebankingsystem.service.StatementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<CustomerOptionDTO>> getCustomerOptions(Principal principal) {
        List<CustomerOptionDTO> customers = statementService.getCustomerOptionsForAdmin(principal.getName());
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<StatementResponse> getStatement(
            @PathVariable String accountNumber,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            Principal principal) {

        StatementResponse response = statementService.getStatement(
                accountNumber, preset, fromDate, toDate, search, type, status, sort, principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Principal principal) throws Exception {

        StatementResponse statement = statementService.getStatement(
                accountNumber, preset, fromDate, toDate, null, "ALL", "ALL", "DATE_ASC", principal.getName());
        byte[] pdfBytes = statementService.generatePdfStatement(statement);

        String filename = String.format("Statement_%s_%s_to_%s.pdf",
                statement.getAccountNumber(), statement.getFromDate(), statement.getToDate());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/download/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Principal principal) throws Exception {

        StatementResponse statement = statementService.getStatement(
                accountNumber, preset, fromDate, toDate, null, "ALL", "ALL", "DATE_ASC", principal.getName());
        byte[] excelBytes = statementService.generateExcelStatement(statement);

        String filename = String.format("Statement_%s_%s_to_%s.xlsx",
                statement.getAccountNumber(), statement.getFromDate(), statement.getToDate());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
