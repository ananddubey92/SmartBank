package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.AiAssistantRequest;
import com.project.onlinebankingsystem.dto.AiAssistantResponse;
import com.project.onlinebankingsystem.service.AiBankingAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AiBankingAssistantService aiBankingAssistantService;

    public AiAssistantController(AiBankingAssistantService aiBankingAssistantService) {
        this.aiBankingAssistantService = aiBankingAssistantService;
    }

    @PostMapping("/assistant")
    public ResponseEntity<AiAssistantResponse> askAssistant(Principal principal,
                                                            @Valid @RequestBody AiAssistantRequest request) {
        AiAssistantResponse response = aiBankingAssistantService.processQuery(principal.getName(), request);
        return ResponseEntity.ok(response);
    }
}
