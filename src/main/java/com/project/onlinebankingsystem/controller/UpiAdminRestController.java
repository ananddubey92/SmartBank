package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.UpiAdminConfigDTO;
import com.project.onlinebankingsystem.service.UpiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/upi")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class UpiAdminRestController {

    private final UpiService upiService;

    public UpiAdminRestController(UpiService upiService) {
        this.upiService = upiService;
    }

    @GetMapping("/config")
    public ResponseEntity<UpiAdminConfigDTO> getUpiConfig() {
        UpiAdminConfigDTO response = upiService.getSuperAdminUpiConfig();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/config")
    public ResponseEntity<UpiAdminConfigDTO> updateUpiConfig(Principal principal, @RequestBody Map<String, Object> body) {
        BigDecimal perTxLimit = body.get("perTransactionLimit") != null ? new BigDecimal(body.get("perTransactionLimit").toString()) : null;
        BigDecimal dailyLimit = body.get("dailyLimit") != null ? new BigDecimal(body.get("dailyLimit").toString()) : null;
        Boolean enabled = body.get("upiEnabled") != null ? Boolean.parseBoolean(body.get("upiEnabled").toString()) : null;

        UpiAdminConfigDTO response = upiService.updateSuperAdminUpiConfig(principal.getName(), perTxLimit, dailyLimit, enabled);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/toggle-lock/{upiId}")
    public ResponseEntity<Map<String, String>> toggleCustomerLock(Principal principal,
                                                                   @PathVariable String upiId,
                                                                   @RequestParam boolean lock) {
        upiService.toggleCustomerUpiLock(principal.getName(), upiId, lock);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Customer UPI " + upiId + " has been " + (lock ? "locked" : "unlocked") + "."
        ));
    }
}
