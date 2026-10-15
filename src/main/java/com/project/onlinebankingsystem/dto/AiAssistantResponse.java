package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAssistantResponse {
    private String answer;
    private String intentDetected;
    private Double confidence;
    private List<String> suggestedQuestions;
    private LocalDateTime timestamp;
    private String engineUsed; // "SmartBank Local Intent Engine" or "LLM Cloud Adapter"
    private String actionUrl;

    public String getIntent() {
        return intentDetected;
    }

    public List<String> getSuggestedActions() {
        return suggestedQuestions;
    }
}
