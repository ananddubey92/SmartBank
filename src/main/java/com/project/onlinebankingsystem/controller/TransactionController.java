package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.DepositWithdrawRequest;
import com.project.onlinebankingsystem.dto.TransactionResponse;
import com.project.onlinebankingsystem.dto.TransferRequest;
import com.project.onlinebankingsystem.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody DepositWithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal) {
        String username = (principal != null) ? principal.getName() : null;
        TransactionResponse response = transactionService.deposit(request.getAccountNumber(), request.getAmount(), "Online Cash Deposit", null, username, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody DepositWithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal) {
        String username = (principal != null) ? principal.getName() : null;
        TransactionResponse response = transactionService.withdraw(request.getAccountNumber(), request.getAmount(), "Online Cash Withdrawal", null, username, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal) {
        String username = (principal != null) ? principal.getName() : null;
        TransactionResponse response = transactionService.transfer(
                request.getSourceAccountNumber(),
                request.getDestinationAccountNumber(),
                request.getAmount(),
                request.getDescription(),
                request.getCategory(),
                request.getOtp(),
                request.getPin(),
                username,
                idempotencyKey
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getHistory(
            @PathVariable String accountNumber,
            Principal principal) {
        String username = (principal != null) ? principal.getName() : null;
        List<TransactionResponse> history = transactionService.getTransactionHistory(accountNumber, username);
        return ResponseEntity.ok(history);
    }
}
