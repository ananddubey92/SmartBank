package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.FinancialHealthResponse;
import com.project.onlinebankingsystem.dto.SpendingPredictionResponse;
import com.project.onlinebankingsystem.service.FinancialHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FinancialHealthController {

    private final FinancialHealthService financialHealthService;

    public FinancialHealthController(FinancialHealthService financialHealthService) {
        this.financialHealthService = financialHealthService;
    }

    @GetMapping("/financial-health")
    public ResponseEntity<FinancialHealthResponse> getFinancialHealth(Principal principal) {
        FinancialHealthResponse response = financialHealthService.calculateFinancialHealth(principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/financial-insights")
    public ResponseEntity<List<String>> getFinancialInsights(Principal principal) {
        FinancialHealthResponse response = financialHealthService.calculateFinancialHealth(principal.getName());
        return ResponseEntity.ok(response.getIntelligentInsights());
    }

    @GetMapping("/spending-prediction")
    public ResponseEntity<SpendingPredictionResponse> getSpendingPrediction(Principal principal) {
        FinancialHealthResponse response = financialHealthService.calculateFinancialHealth(principal.getName());
        return ResponseEntity.ok(response.getSpendingPrediction());
    }
}
