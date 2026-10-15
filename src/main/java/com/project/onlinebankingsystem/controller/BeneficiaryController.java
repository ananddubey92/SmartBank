package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.BeneficiaryRequest;
import com.project.onlinebankingsystem.dto.BeneficiaryResponse;
import com.project.onlinebankingsystem.model.Beneficiary;
import com.project.onlinebankingsystem.service.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getMyBeneficiaries(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        List<BeneficiaryResponse> beneficiaries = beneficiaryService.getBeneficiariesByUsername(principal.getName())
                .stream().map(BeneficiaryResponse::fromEntity).toList();
        return ResponseEntity.ok(beneficiaries);
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> addBeneficiary(Principal principal, @Valid @RequestBody BeneficiaryRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        Beneficiary beneficiary = beneficiaryService.addBeneficiary(principal.getName(), request);
        return ResponseEntity.ok(BeneficiaryResponse.fromEntity(beneficiary));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBeneficiary(Principal principal, @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        beneficiaryService.deleteBeneficiary(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
