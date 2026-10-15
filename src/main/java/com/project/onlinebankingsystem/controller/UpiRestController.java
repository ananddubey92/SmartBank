package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.service.UpiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/upi")
public class UpiRestController {

    private final UpiService upiService;

    public UpiRestController(UpiService upiService) {
        this.upiService = upiService;
    }

    @GetMapping("/check-availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(@RequestParam String upiId) {
        boolean available = upiService.isUpiAvailable(upiId);
        return ResponseEntity.ok(Map.of("upiId", upiId, "available", available));
    }

    @PostMapping("/create")
    public ResponseEntity<UpiProfileResponse> createUpiId(Principal principal, @Valid @RequestBody CreateUpiIdRequest req) {
        UpiProfileResponse response = upiService.createUpiId(principal.getName(), req.getCustomUpiId(), req.getAccountNumber());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile")
    public ResponseEntity<UpiProfileResponse> getProfile(Principal principal) {
        UpiProfileResponse response = upiService.getUpiProfile(principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<UpiProfileResponse> updateProfile(Principal principal, @RequestBody Map<String, String> body) {
        String newUpiId = body.get("upiId");
        UpiProfileResponse response = upiService.updateUpiId(principal.getName(), newUpiId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/status")
    public ResponseEntity<UpiProfileResponse> toggleStatus(Principal principal, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        UpiProfileResponse response = upiService.toggleUpiStatus(principal.getName(), active);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pin/set")
    public ResponseEntity<Map<String, String>> setPin(Principal principal, @Valid @RequestBody UpiPinRequest req) {
        upiService.setUpiPin(principal.getName(), req.getPin(), req.getConfirmPin(), req.getAccountPassword());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "UPI PIN set successfully"));
    }

    @PostMapping("/pin/change")
    public ResponseEntity<Map<String, String>> changePin(Principal principal, @Valid @RequestBody UpiPinRequest req) {
        upiService.changeUpiPin(principal.getName(), req.getOldPin(), req.getPin(), req.getConfirmPin());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "UPI PIN changed successfully"));
    }

    @PostMapping("/pin/reset")
    public ResponseEntity<Map<String, String>> resetPin(Principal principal, @RequestBody UpiPinRequest req) {
        upiService.resetUpiPin(principal.getName(), req.getPin(), req.getConfirmPin(), req.getAccountPassword(), req.getOtp());
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "UPI PIN reset successfully and lock cleared"));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPin(Principal principal, @RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        boolean valid = upiService.verifyUpiPin(principal.getName(), pin);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "valid", valid));
    }

    @PostMapping("/send")
    public ResponseEntity<UpiTransactionResponse> sendMoney(Principal principal, @Valid @RequestBody UpiSendMoneyRequest req) {
        UpiTransactionResponse response = upiService.sendMoney(principal.getName(), req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/request")
    public ResponseEntity<UpiMoneyRequestDTO> createRequest(Principal principal, @RequestBody Map<String, Object> body) {
        String targetUpiId = (String) body.get("targetUpiId");
        java.math.BigDecimal amount = new java.math.BigDecimal(body.get("amount").toString());
        String remarks = (String) body.get("remarks");

        UpiMoneyRequestDTO response = upiService.createMoneyRequest(principal.getName(), targetUpiId, amount, remarks);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<Map<String, List<UpiMoneyRequestDTO>>> getRequests(Principal principal) {
        List<UpiMoneyRequestDTO> received = upiService.getReceivedRequests(principal.getName());
        List<UpiMoneyRequestDTO> sent = upiService.getSentRequests(principal.getName());
        return ResponseEntity.ok(Map.of("received", received, "sent", sent));
    }

    @PutMapping("/request/{id}/accept")
    public ResponseEntity<UpiTransactionResponse> acceptRequest(Principal principal,
                                                                @PathVariable Long id,
                                                                @RequestBody(required = false) Map<String, String> body) {
        String pin = body != null ? body.get("upiPin") : null;
        String otp = body != null ? body.get("otp") : null;
        UpiTransactionResponse response = upiService.acceptMoneyRequest(principal.getName(), id, pin, otp);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/request/{id}/reject")
    public ResponseEntity<UpiMoneyRequestDTO> rejectRequest(Principal principal, @PathVariable Long id) {
        UpiMoneyRequestDTO response = upiService.rejectMoneyRequest(principal.getName(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/request/{id}/cancel")
    public ResponseEntity<UpiMoneyRequestDTO> cancelRequest(Principal principal, @PathVariable Long id) {
        UpiMoneyRequestDTO response = upiService.cancelMoneyRequest(principal.getName(), id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/qr/generate")
    public ResponseEntity<UpiQrResponse> generateQr(Principal principal) {
        UpiQrResponse response = upiService.generatePersonalQr(principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/qr/scan")
    public ResponseEntity<Map<String, String>> scanQr(@RequestBody Map<String, String> body) {
        String content = body.get("qrContent");
        Map<String, String> parsed = upiService.scanQrContent(content);
        return ResponseEntity.ok(parsed);
    }

    @PostMapping("/qr/pay")
    public ResponseEntity<UpiTransactionResponse> payQr(Principal principal, @Valid @RequestBody UpiQrPayRequest req) {
        UpiTransactionResponse response = upiService.payQr(principal.getName(), req);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<UpiTransactionResponse>> getTransactions(Principal principal,
                                                                        @RequestParam(required = false) String direction,
                                                                        @RequestParam(required = false) String status,
                                                                        @RequestParam(required = false) String search) {
        List<UpiTransactionResponse> response = upiService.getUpiTransactions(principal.getName(), direction, status, search);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{ref}")
    public ResponseEntity<UpiReceiptResponse> getReceipt(Principal principal, @PathVariable String ref) {
        UpiReceiptResponse response = upiService.getPaymentReceipt(principal.getName(), ref);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/limits")
    public ResponseEntity<UpiLimitsResponse> getLimits(Principal principal) {
        UpiLimitsResponse response = upiService.getUpiLimits(principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics")
    public ResponseEntity<UpiAnalyticsResponse> getAnalytics(Principal principal) {
        UpiAnalyticsResponse response = upiService.getUpiAnalytics(principal.getName());
        return ResponseEntity.ok(response);
    }
}
