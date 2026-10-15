package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.DebitCardResponse;
import com.project.onlinebankingsystem.dto.DebitCardStatusUpdateRequest;
import com.project.onlinebankingsystem.enums.CardStatus;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.service.DebitCardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/debit-cards")
@PreAuthorize("isAuthenticated()")
public class DebitCardController {

    private final DebitCardService debitCardService;

    public DebitCardController(DebitCardService debitCardService) {
        this.debitCardService = debitCardService;
    }

    /**
     * Get the primary debit card of the logged-in customer.
     * Masked format (e.g. **** **** **** 1234) returned.
     */
    @GetMapping("/my-card")
    public ResponseEntity<DebitCardResponse> getMyDebitCard(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        DebitCardResponse card = debitCardService.getCardForCustomer(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("No active Debit Card found for customer: " + principal.getName()));
        return ResponseEntity.ok(card);
    }

    /**
     * Get all debit cards belonging to the logged-in customer.
     */
    @GetMapping
    public ResponseEntity<List<DebitCardResponse>> getMyDebitCards(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        List<DebitCardResponse> cards = debitCardService.getCardsForCustomer(principal.getName());
        return ResponseEntity.ok(cards);
    }

    /**
     * Customer can securely toggle their card between ACTIVE and BLOCKED.
     */
    @PostMapping("/{cardId}/toggle-status")
    public ResponseEntity<DebitCardResponse> toggleCardStatus(@PathVariable Long cardId,
                                                              Principal principal,
                                                              HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String ip = request.getRemoteAddr();
        DebitCardResponse updated = debitCardService.toggleCardStatusByCustomer(cardId, principal.getName(), ip);
        return ResponseEntity.ok(updated);
    }

    /**
     * Customer updates specific card status (e.g. ACTIVE or BLOCKED) with verification.
     */
    @PatchMapping("/{cardId}/status")
    public ResponseEntity<DebitCardResponse> updateCardStatus(@PathVariable Long cardId,
                                                              @Valid @RequestBody DebitCardStatusUpdateRequest requestDto,
                                                              Principal principal,
                                                              HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String ip = request.getRemoteAddr();
        CardStatus status;
        try {
            status = CardStatus.valueOf(requestDto.getStatus().trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid status. Supported values: ACTIVE, BLOCKED");
        }

        if (status != CardStatus.ACTIVE && status != CardStatus.BLOCKED) {
            throw new IllegalArgumentException("Customers can only set status to ACTIVE or BLOCKED.");
        }

        DebitCardResponse current = debitCardService.getCardForCustomer(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Debit Card not found"));

        if (!current.getStatus().equalsIgnoreCase(status.name())) {
            current = debitCardService.toggleCardStatusByCustomer(cardId, principal.getName(), ip);
        }

        return ResponseEntity.ok(current);
    }

    /**
     * Customer retrieves full unmasked card details (Full card number & CVV)
     * strictly protected by re-authentication and ownership verification.
     */
    @GetMapping("/{cardId}/reveal")
    public ResponseEntity<DebitCardResponse> revealCardDetails(@PathVariable Long cardId,
                                                               @RequestParam(required = false) String pin,
                                                               @RequestParam(required = false) String password,
                                                               Principal principal,
                                                               HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String pinOrPassword = (pin != null && !pin.isBlank()) ? pin : password;
        if (pinOrPassword == null || pinOrPassword.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Security PIN or Password is required to reveal unmasked card details.");
        }
        String ip = request.getRemoteAddr();
        DebitCardResponse card = debitCardService.revealCardDetails(cardId, principal.getName(), pinOrPassword, ip);
        return ResponseEntity.ok(card);
    }

    @PostMapping("/{cardId}/reveal")
    public ResponseEntity<DebitCardResponse> revealCardDetailsPost(@PathVariable Long cardId,
                                                                   @RequestBody(required = false) com.project.onlinebankingsystem.dto.DebitCardRevealRequest revealRequest,
                                                                   Principal principal,
                                                                   HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String pinOrPassword = (revealRequest != null) ? 
                (revealRequest.getPin() != null && !revealRequest.getPin().isBlank() ? revealRequest.getPin() : revealRequest.getPassword()) : null;
        if (pinOrPassword == null || pinOrPassword.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Security PIN or Password is required to reveal unmasked card details.");
        }
        String ip = request.getRemoteAddr();
        DebitCardResponse card = debitCardService.revealCardDetails(cardId, principal.getName(), pinOrPassword, ip);
        return ResponseEntity.ok(card);
    }

    /**
     * Customer requests replacement of a damaged or compromised card.
     */
    @PostMapping("/{cardId}/replace")
    public ResponseEntity<DebitCardResponse> replaceDebitCard(@PathVariable Long cardId,
                                                              Principal principal,
                                                              HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String ip = request.getRemoteAddr();
        DebitCardResponse newCard = debitCardService.replaceCard(cardId, principal.getName(), ip);
        return ResponseEntity.ok(newCard);
    }
}

